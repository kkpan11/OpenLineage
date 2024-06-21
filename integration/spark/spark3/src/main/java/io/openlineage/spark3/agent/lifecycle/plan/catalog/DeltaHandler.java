/*
/* Copyright 2018-2024 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.spark3.agent.lifecycle.plan.catalog;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.utils.DatasetIdentifier;
import io.openlineage.spark.agent.util.PathUtils;
import io.openlineage.spark.api.OpenLineageContext;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.TableIdentifier;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.delta.catalog.DeltaCatalog;
import org.apache.spark.sql.delta.catalog.DeltaTableV2;
import scala.Option;

@Slf4j
public class DeltaHandler implements CatalogHandler {

  private final OpenLineageContext context;
  private static final String DEFAULT_SEPARATOR = "/";
  private static final String DEFAULT_SCHEME = "file";

  public DeltaHandler(OpenLineageContext context) {
    this.context = context;
  }

  @Override
  public boolean hasClasses() {
    try {
      DeltaHandler.class
          .getClassLoader()
          .loadClass("org.apache.spark.sql.delta.catalog.DeltaCatalog");
      return true;
    } catch (Exception e) {
      // swallow- we don't care
    }
    return false;
  }

  @Override
  public boolean isClass(TableCatalog tableCatalog) {
    return tableCatalog instanceof DeltaCatalog;
  }

  @Override
  public DatasetIdentifier getDatasetIdentifier(
      SparkSession session,
      TableCatalog tableCatalog,
      Identifier identifier,
      Map<String, String> properties) {
    DeltaCatalog catalog = (DeltaCatalog) tableCatalog;

    Optional<String> location;
    if (catalog.isPathIdentifier(identifier)) {
      location = Optional.of(identifier.name());
    } else {
      location = Optional.ofNullable(properties.get("location"));
    }

    // Delta uses spark2 catalog when location isn't specified.
    Path path =
        new Path(
            location.orElseGet(
                () -> {
                  try {
                    return Optional.ofNullable(
                            catalog.loadTable(identifier).properties().get("location"))
                        .orElseGet(() -> getDefaultTablePath(session, identifier));
                  } catch (Exception e) {
                    // loadTable failed
                    return getDefaultTablePath(session, identifier);
                  }
                }));
    URI uri = prepareUriFromPath(path);

    DatasetIdentifier di = PathUtils.fromPath(path);

    return di.withSymlink(
        identifier.toString(),
        StringUtils.substringBeforeLast(
            uri.toString(), File.separator), // parent location from a name becomes a namespace
        DatasetIdentifier.SymlinkType.TABLE);
  }

  @SneakyThrows
  private static URI prepareUriFromPath(Path path) {
    URI uri = path.toUri();

    if (uri.getPath() != null
        && uri.getPath().startsWith(DEFAULT_SEPARATOR)
        && uri.getScheme() == null) {
      uri = new URI(DEFAULT_SCHEME, null, uri.getPath(), null, null);
    }

    return uri;
  }

  private String getDefaultTablePath(SparkSession session, Identifier identifier) {
    return session
        .sessionState()
        .catalog()
        .defaultTablePath(
            TableIdentifier.apply(
                identifier.name(),
                Option.apply(
                    Arrays.stream(identifier.namespace()).reduce((x, y) -> y).orElse(null))))
        .toString();
  }

  @Override
  public Optional<OpenLineage.StorageDatasetFacet> getStorageDatasetFacet(
      Map<String, String> properties) {
    return Optional.of(
        context
            .getOpenLineage()
            .newStorageDatasetFacet("delta", "parquet")); // Delta is always parquet
  }

  @SneakyThrows
  @Override
  public Optional<String> getDatasetVersion(
      TableCatalog tableCatalog, Identifier identifier, Map<String, String> properties) {
    DeltaCatalog deltaCatalog = (DeltaCatalog) tableCatalog;
    Table table = deltaCatalog.loadTable(identifier);

    if (table instanceof DeltaTableV2) {
      DeltaTableV2 deltaTable = (DeltaTableV2) table;
      return Optional.of(Long.toString(deltaTable.snapshot().version()));
    }
    return Optional.empty();
  }

  @Override
  public String getName() {
    return "delta";
  }
}
