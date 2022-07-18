package io.openlineage.flink.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.openlineage.client.OpenLineage;
import io.openlineage.flink.agent.client.EventEmitter;
import io.openlineage.flink.api.OpenLineageContext;
import io.openlineage.flink.visitor.wrapper.IcebergSourceWrapper;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.source.StreamingMonitorFunction;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class IcebergSourceVisitorTest {
  OpenLineageContext context = mock(OpenLineageContext.class);
  StreamingMonitorFunction icebergSource = mock(StreamingMonitorFunction.class);
  IcebergSourceWrapper wrapper = mock(IcebergSourceWrapper.class);
  IcebergSourceVisitor icebergSourceVisitor = new IcebergSourceVisitor(context);
  OpenLineage openLineage = new OpenLineage(EventEmitter.OPEN_LINEAGE_CLIENT_URI);

  @BeforeEach
  @SneakyThrows
  public void setup() {
    when(context.getOpenLineage()).thenReturn(openLineage);
  }

  @Test
  public void testIsDefined() {
    assertEquals(false, icebergSourceVisitor.isDefinedAt(mock(Object.class)));
    assertEquals(true, icebergSourceVisitor.isDefinedAt(mock(StreamingMonitorFunction.class)));
  }

  @Test
  @SneakyThrows
  public void testApply() {
    Table table = mock(Table.class, RETURNS_DEEP_STUBS);

    try (MockedStatic<IcebergSourceWrapper> mockedStatic = mockStatic(IcebergSourceWrapper.class)) {
      when(IcebergSourceWrapper.of(icebergSource)).thenReturn(wrapper);
      when(table.location()).thenReturn("s3://bucket/table/");
      when(table.schema().columns())
          .thenReturn(
              Collections.singletonList(Types.NestedField.of(1, false, "a", Types.LongType.get())));
      when(wrapper.getTable()).thenReturn(table);

      List<OpenLineage.InputDataset> inputDatasets = icebergSourceVisitor.apply(icebergSource);
      List<OpenLineage.SchemaDatasetFacetFields> fields =
          inputDatasets.get(0).getFacets().getSchema().getFields();

      assertEquals(1, inputDatasets.size());
      assertEquals("table", inputDatasets.get(0).getName());
      assertEquals("s3://bucket", inputDatasets.get(0).getNamespace());

      assertEquals(1, fields.size());
      assertEquals("a", fields.get(0).getName());
      assertEquals("LONG", fields.get(0).getType());
    }
  }
}