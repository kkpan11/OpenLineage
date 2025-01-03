/*
/* Copyright 2018-2025 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.spark.agent.vendor.snowflake.lifecycle;

import io.openlineage.client.OpenLineage;
import io.openlineage.spark.agent.lifecycle.VisitorFactory;
import io.openlineage.spark.api.DatasetFactory;
import io.openlineage.spark.api.OpenLineageContext;
import java.util.Collections;
import java.util.List;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import scala.PartialFunction;

public class SnowflakeVisitorFactory implements VisitorFactory {
  @Override
  public List<PartialFunction<LogicalPlan, List<OpenLineage.InputDataset>>> getInputVisitors(
      OpenLineageContext context) {
    DatasetFactory<OpenLineage.InputDataset> factory = DatasetFactory.input(context);
    return Collections.singletonList(new SnowflakeRelationVisitor<>(context, factory));
  }

  @Override
  public List<PartialFunction<LogicalPlan, List<OpenLineage.OutputDataset>>> getOutputVisitors(
      OpenLineageContext context) {
    DatasetFactory<OpenLineage.OutputDataset> factory = DatasetFactory.output(context);
    return Collections.singletonList(new SnowflakeRelationVisitor<>(context, factory));
  }
}
