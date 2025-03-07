/*
/* Copyright 2018-2025 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.flink;

import static java.nio.file.Files.readAllBytes;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import com.google.common.io.Resources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.model.RequestDefinition;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("PMD")
@Tag("integration-test")
@Testcontainers
@Slf4j
class ContainerTest {

  private static final Network network = Network.newNetwork();
  private static MockServerClient mockServerClient;

  @Container
  private static final MockServerContainer openLineageClientMockContainer =
      FlinkContainerUtils.makeMockServerContainer(network);

  @Container
  private static final GenericContainer zookeeper =
      FlinkContainerUtils.makeZookeeperContainer(network);

  @Container
  private static final GenericContainer kafka =
      FlinkContainerUtils.makeKafkaContainer(network, zookeeper);

  @Container
  private static final GenericContainer schemaRegistry =
      FlinkContainerUtils.makeSchemaRegistryContainer(network, kafka);

  @Container
  private static final GenericContainer generateEvents =
      FlinkContainerUtils.makeGenerateEventsContainer(network, schemaRegistry);

  private static GenericContainer jobManager;

  private static GenericContainer taskManager;

  @BeforeAll
  public static void setup() {
    mockServerClient =
        new MockServerClient(
            openLineageClientMockContainer.getHost(),
            openLineageClientMockContainer.getServerPort());

    mockServerClient
        .when(request("/api/v1/lineage"))
        .respond(org.mockserver.model.HttpResponse.response().withStatusCode(201));

    await().until(openLineageClientMockContainer::isRunning);
  }

  @AfterEach
  public void cleanup() {
    mockServerClient.clear(request("/api/v1/lineage"), ClearType.LOG);

    try {
      if (taskManager != null) {
        taskManager.stop();
      }
    } catch (Exception e2) {
      log.error("Unable to shut down taskmanager container", e2);
    }
    try {
      if (jobManager != null) {
        jobManager.stop();
      }
    } catch (Exception e2) {
      log.error("Unable to shut down jobmanager container", e2);
    }
  }

  @AfterAll
  public static void tearDown() {
    FlinkContainerUtils.stopAll(
        Arrays.asList(
            openLineageClientMockContainer,
            zookeeper,
            schemaRegistry,
            kafka,
            generateEvents,
            jobManager,
            taskManager));

    network.close();
  }

  void runUntilLogMessage(
      String entrypointClass,
      Properties jobProperties,
      GenericContainer prepareContainer,
      String logMessage) {
    jobManager =
        FlinkContainerUtils.makeFlinkJobManagerContainer(
            entrypointClass,
            network,
            Arrays.asList(prepareContainer, openLineageClientMockContainer),
            jobProperties);
    taskManager =
        FlinkContainerUtils.makeFlinkTaskManagerContainer(network, Arrays.asList(jobManager));
    taskManager.start();
    await()
        .atMost(Duration.ofMinutes(5))
        .until(() -> FlinkContainerUtils.verifyJobManagerLogs(jobManager, logMessage));
  }

  HttpRequest getEvent(String path) {
    return request()
        .withPath("/api/v1/lineage")
        .withBody(readJson(Path.of(Resources.getResource(path).getPath())));
  }

  void verify(String... eventFiles) {
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () ->
                mockServerClient.verify(
                    Arrays.stream(eventFiles)
                        .map(this::getEvent)
                        .collect(Collectors.toList())
                        .toArray(new RequestDefinition[0])));
  }

  @Test
  @SneakyThrows
  void testSqlKafkaJob() {
    runUntilLogMessage(
        "io.openlineage.flink.FlinkSqlApplication",
        new Properties(),
        generateEvents,
        "insert-into_default_catalog.default_database.kafka_output");

    verify("events/expected_sql_kafka.json");
  }

  @Test
  @SneakyThrows
  void testKafkaTopicPatternJob() {
    Properties jobProperties = new Properties();

    jobProperties.put("inputTopics", "io.openlineage.flink.kafka.input.*");
    jobProperties.put("outputTopics", "io.openlineage.flink.kafka.output_topic");
    jobProperties.put("jobName", "flink_topic_pattern");

    runUntilLogMessage(
        "io.openlineage.flink.FlinkTopicPatternApplication",
        jobProperties,
        generateEvents,
        "Emitting checkpoint event");

    verify(
        "events/expected_kafka_topic_pattern.json",
        "events/expected_kafka_topic_pattern_checkpoint.json");
  }

  @SneakyThrows
  private JsonBody readJson(Path path) {
    return json(new String(readAllBytes(path)), MatchType.ONLY_MATCHING_FIELDS);
  }
}
