/*
/* Copyright 2018-2024 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openlineage.client.circuitBreaker.CircuitBreakerConfig;
import io.openlineage.client.config.JobConfig;
import io.openlineage.client.transports.FacetsConfig;
import io.openlineage.client.transports.TransportConfig;
import lombok.Getter;

/** Configuration for {@link OpenLineageClient}. */
public class OpenLineageYaml {
  @Getter
  @JsonProperty("transport")
  private TransportConfig transportConfig;

  @Getter
  @JsonProperty("facets")
  private FacetsConfig facetsConfig;

  @Getter
  @JsonProperty("circuitBreaker")
  private CircuitBreakerConfig circuitBreaker;

  @Getter
  @JsonProperty("job")
  private JobConfig jobConfig;
}
