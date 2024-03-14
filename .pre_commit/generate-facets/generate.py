# Copyright 2018-2024 contributors to the OpenLineage project
# SPDX-License-Identifier: Apache-2.0
from __future__ import annotations

import copy
import itertools
import json
import os
import pathlib
import re
import shutil
import tempfile
from collections import defaultdict

import click
from datamodel_code_generator import DataModelType, PythonVersion
from datamodel_code_generator.imports import Import
from datamodel_code_generator.model import get_data_model_types
from datamodel_code_generator.parser.jsonschema import JsonSchemaParser
from datamodel_code_generator.types import Types

_UNDER_SCORE_1 = re.compile(r"([^_])([A-Z][a-z]+)")
_UNDER_SCORE_2 = re.compile("([a-z0-9])([A-Z])")


def camel_to_snake(string: str) -> str:
    subbed = _UNDER_SCORE_1.sub(r"\1_\2", string)
    return _UNDER_SCORE_2.sub(r"\1_\2", subbed).lower()


# import attrs instead of dataclass
data_model_types = get_data_model_types(
    DataModelType.DataclassesDataclass, target_python_version=PythonVersion.PY_38
)
new_model = data_model_types.data_model
new_model.DEFAULT_IMPORTS = (
    Import.from_full_path("attr.define"),
    Import.from_full_path("attr.field"),
)

# locations definitions
base_spec_location = pathlib.Path(__file__).resolve().parent.parent.parent / "spec" / "OpenLineage.json"
facets_spec_location = pathlib.Path(__file__).resolve().parent.parent.parent / "spec" / "facets"
default_output_location = (
    pathlib.Path(__file__).resolve().parent.parent.parent
    / "client"
    / "python"
    / "openlineage"
    / "client"
    / "generated"
)
templates_location = pathlib.Path(__file__).resolve().parent / "templates"
python_client_location = pathlib.Path(__file__).resolve().parent.parent.parent / "client" / "python"

# custom code
set_producer_code = """
PRODUCER = DEFAULT_PRODUCER

def set_producer(producer: str) -> None:
    global PRODUCER  # noqa: PLW0603
    PRODUCER = producer
"""
header = (
    "# Copyright 2018-2024 contributors to the OpenLineage project\n"
    "# SPDX-License-Identifier: Apache-2.0\n\n"
)

# structures to customize code generation
redact_fields = {
    "Assertion": ["column"],
    "InputField": ["namespace", "name", "field"],
    "Dataset": ["namespace", "name"],
    "InputDataset": ["namespace", "name"],
    "OutputDataset": ["namespace", "name"],
    "Job": ["namespace", "name"],
    "Run": ["runId"],
    "RunEvent": ["eventType", "eventTime"],
    "NominalTimeRunFacet": ["nominalStartTime", "nominalEndTime"],
    "ParentRunFacet": ["job", "run"],
    "SourceCodeLocationJobFacet": ["type", "url"],
    "DatasourceDatasetFacet": ["name", "uri"],
    "OutputStatisticsOutputDatasetFacet": ["rowCount", "size"],
    "SourceCodeJobFacet": ["language"],
    "ErrorMessageRunFacet": ["programmingLanguage"],
}
schema_urls = {}
base_ids = {}


def merge_dicts(dict1, dict2):
    merged = dict1.copy()
    for k, v in dict2.items():
        if k in merged and isinstance(v, dict):
            merged[k] = merge_dicts(merged.get(k, {}), v)
        else:
            merged[k] = v
    return merged


def load_specs(base_spec_location: pathlib.Path, facets_spec_location: pathlib.Path) -> list[pathlib.Path]:
    """Load base `OpenLineage.json` and other facets' spec files"""
    locations = []

    for file_spec in itertools.chain(
        [base_spec_location.resolve()],
        sorted(pathlib.Path(os.path.abspath(facets_spec_location)).glob("*.json")),
    ):
        spec = json.load(file_spec.open())
        parse_additional_data(spec, file_spec.name)
        locations.append(file_spec)

    return locations


def parse_additional_data(spec, file_name):
    """Parse additional data from spec files.

    Parses:
        * schema URLs
        * base IDs
    """
    base_id = spec["$id"]
    for name, _ in spec["$defs"].items():
        schema_urls[name] = f"{base_id}#/$defs/{name}"
        base_ids[file_name] = spec["$id"]


def parse_and_generate(locations):
    """Parse and generate data models from a given specification."""

    extra_schema_urls = {obj_name: {"_schemaURL": schema_url} for obj_name, schema_url in schema_urls.items()}
    extra_redact_fields = {obj_name: {"redact_fields": fields} for obj_name, fields in redact_fields.items()}

    temporary_locations = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp_directory = pathlib.Path(tmp).resolve()
        for location in locations:
            tmp_location = (tmp_directory / location.name).resolve()
            tmp_location.write_text(location.read_text())
            temporary_locations.append(tmp_location)

        os.chdir(tmp_directory)
        # first parse OpenLineage.json
        parser = JsonSchemaParser(
            source=temporary_locations[:1],
            data_model_type=new_model,
            data_model_root_type=data_model_types.root_model,
            data_model_field_type=data_model_types.field_model,
            data_type_manager_type=data_model_types.data_type_manager,
            dump_resolve_reference_action=data_model_types.dump_resolve_reference_action,
            special_field_name_prefix="",
            use_schema_description=True,
            field_constraints=True,
            use_union_operator=True,
            use_standard_collections=True,
            base_class="openlineage.client.utils.RedactMixin",
            class_name="ClassToBeSkipped",
            use_field_description=True,
            use_double_quotes=True,
            keep_model_order=True,
            custom_template_dir=templates_location,
            extra_template_data=defaultdict(dict, merge_dicts(extra_redact_fields, extra_schema_urls)),
            additional_imports=["typing.ClassVar", "openlineage.client.constants.DEFAULT_PRODUCER"],
        )

        # keep information about uuid and date-time formats
        # this is going to be changed back to str type hint in jinja template
        uuid_type = copy.deepcopy(parser.data_type_manager.type_map[Types.uuid])
        uuid_type.type = "uuid"
        parser.data_type_manager.type_map[Types.uuid] = uuid_type

        date_time_type = copy.deepcopy(parser.data_type_manager.type_map[Types.date_time])
        date_time_type.type = "date-time"
        parser.data_type_manager.type_map[Types.date_time] = date_time_type

        parser.parse(format_=False)

        # parse rest of spec
        parser.source = temporary_locations[1:]

        # change paths so that parser sees base objects as local
        parser.model_resolver.references = {
            k.replace("OpenLineage.json", base_ids["OpenLineage.json"]): v
            for k, v in parser.model_resolver.references.items()
        }

        output = parser.parse(format_=False)

    # go back to client location
    os.chdir(python_client_location)

    return output


def format_and_save_output(output: str, location: pathlib.Path):
    """Adjust and format generated file."""
    import subprocess

    # save temporary file to the same directory that output
    # so that ruff receives same rules
    with tempfile.NamedTemporaryFile(
        "w", prefix=location.stem.lower(), suffix=".py", dir=default_output_location.parent, delete=False
    ) as tmpfile:
        tmpfile.write(header)
        is_base_module = "from ." not in output
        tmpfile.write(output.replace("from .OpenLineage", "from openlineage.client.generated.base"))
        if is_base_module:
            tmpfile.write(set_producer_code)
        tmpfile.flush()

    # run ruff lint
    with subprocess.Popen(
        args=["ruff", tmpfile.name, "--fix"], stderr=subprocess.STDOUT, close_fds=True
    ) as lint_process:
        if lint_process.returncode:
            print(f"Ruff lint failed: {lint_process.returncode}")

    # run ruff format
    with subprocess.Popen(
        args=["ruff", "format", tmpfile.name],
        stderr=subprocess.STDOUT,
        close_fds=True,
    ) as format_process:
        if format_process.returncode:
            print(f"Ruff format failed: {format_process.returncode}")

    # move file to output location
    if location.name == "open_lineage.py":
        location = location.with_name("base.py")
    os.rename(tmpfile.name, location)

    return lint_process.returncode or format_process.returncode


@click.command()
@click.option("--output-location", type=pathlib.Path, default=default_output_location)
def main(output_location):
    locations = load_specs(base_spec_location=base_spec_location, facets_spec_location=facets_spec_location)
    results = parse_and_generate(locations)
    modules = {
        output_location.joinpath(*[camel_to_snake(n.replace("Facet", "")) for n in name]): result.body
        for name, result in sorted(results.items())
    }
    shutil.rmtree(output_location)
    for path, body in modules.items():
        if path and not path.parent.exists():
            path.parent.mkdir(parents=True)
        if body:
            format_and_save_output(body, path)


if __name__ == "__main__":
    main()
