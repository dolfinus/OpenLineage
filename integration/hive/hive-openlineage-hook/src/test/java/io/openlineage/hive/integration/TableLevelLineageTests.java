/*
/* Copyright 2018-2025 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/
package io.openlineage.hive.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration-test")
public class TableLevelLineageTests extends ContainerHiveTestBase {

  public static final String HIVE_TEST_TABLE_DDL = "a INT, b DATE";

  @Test
  public void testSelectAll() {
    createManagedHiveTable("test_table1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("SELECT * FROM test_table1");
    assertThat(mockServerClient.retrieveRecordedRequests(request().withPath("/api/v1/lineage")))
        .isEmpty();
  }

  @Test
  public void testCreateTable() {
    runHiveQuery("CREATE TABLE t1 (a INT, b STRING)");
    verifyEvents("simpleCreateTable.json");
  }

  @Test
  public void testInsertValues() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("INSERT INTO t1 VALUES (99, '2020-01-01')");
    verifyEvents("simpleInsert.json");
  }

  @Test
  public void testDropTable() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("DROP TABLE t1");
    verifyEvents("simpleDropTable.json");
  }

  @Test
  public void testTruncateTable() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("TRUNCATE TABLE t1");
    verifyEvents("simpleTruncateTable.json");
  }

  @Test
  public void testMsckTable() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("MSCK REPAIR TABLE t1");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableRename() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 RENAME TO t2");
    verifyEvents("simpleAlterTableRename.json");
  }

  @Test
  public void testAlterTableSetTblProperties() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 SET TBLPROPERTIES ('a'='b')");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetOwner() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 SET OWNER user abc");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetLocation() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 SET LOCATION '/opt/hive/data/warehouse/custom'");
    verifyEvents("simpleAlterTableSetLocation.json");
  }

  @Test
  public void testAlterTableClusteredBy() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 CLUSTERED BY (b) INTO 10 BUCKETS");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetSkewedBy() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 SKEWED BY (b) ON (1, 2, 3) STORED AS DIRECTORIES");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetSkewedLocation() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 SET SKEWED LOCATION (1='file:/opt/hive/data/warehouse/t1/b=1')");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableUnsetSkewed() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 NOT SKEWED");
    verifyEvents("simpleAlterTable.json");
  }

  @Test
  public void testAlterTableAddPartition() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 ADD IF NOT EXISTS PARTITION (b='2020-01-02') LOCATION '/opt/hive/data/warehouse/t1/b=2020-01-02'");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableDropPartition() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 DROP PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 DROP IF EXISTS PARTITION (b='2020-01-02') IGNORE PROTECTION PURGE");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableAlterPartitionSetLocation() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') SET LOCATION '/opt/hive/data/warehouse/t1/b=2020-01-01'");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableAlterSetFileformat() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 SET FILEFORMAT ORC");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') SET FILEFORMAT ORC");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetSerde() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 SET SERDE 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') SET SERDE 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableSetSerdeProperties() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 SET SERDEPROPERTIES ('a'='b')");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') SET SERDEPROPERTIES ('a'='b')");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableTouchPartition() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 TOUCH PARTITION (b='2020-01-01')");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableArchivePartition() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ARCHIVE PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 UNARCHIVE PARTITION (b='2020-01-01')");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableCompact() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 COMPACT");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') COMPACT");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableConcatenate() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 CONCATENATE");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') CONCATENATE");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableUpdateColumns() {
    runHiveQuery("CREATE TABLE t1 (a INT) PARTITIONED BY (b DATE) STORED AS PARQUET");
    runHiveQuery("ALTER TABLE t1 ADD PARTITION (b='2020-01-01')");
    runHiveQuery("ALTER TABLE t1 UPDATE COLUMNS");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') UPDATE COLUMNS");
    verifyEvents("simpleAlterTable.json", "simpleAlterTable.json", "simpleAlterTable.json");
  }

  @Test
  public void testAlterTableAddColumn() {
    createPartitionedHiveTable("t1", "a int", "b DATE");
    runHiveQuery("ALTER TABLE t1 ADD COLUMNS (fl FLOAT)");
    runHiveQuery("ALTER TABLE t1 PARTITION (b='2020-01-01') ADD COLUMNS (fl2 FLOAT)");
    verifyEvents("simpleAlterTableAddColumn.json", "simpleAlterTableAddColumn.json");
  }

  @Test
  public void testAlterTableChangeColumn() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 CHANGE COLUMN b text STRING COMMENT 'new comment'");
    verifyEvents("simpleAlterTableChangeColumn.json");
  }

  @Test
  public void testAlterTableReplaceColumns() {
    createManagedHiveTable("t1", HIVE_TEST_TABLE_DDL);
    runHiveQuery("ALTER TABLE t1 REPLACE COLUMNS (a a1 DOUBLE)");
    verifyEvents("simpleAlterTableReplaceColumns.json");
  }

  @Test
  public void testFailure() {
    createManagedHiveTable("unemployees", "id int, name string, team int");
    createManagedHiveTable("failure_table", "id int, name string, team int");
    runHiveQuery("INSERT INTO unemployees VALUES(1, 'hello', 1)");
    Assertions.assertThatThrownBy(
            () -> {
              runHiveQuery(
                  String.join(
                      "\n",
                      "INSERT INTO failure_table",
                      "SELECT",
                      "    id,",
                      "    name,",
                      "    ASSERT_TRUE(team != 1) as team", // This will fail for team=1
                      "FROM unemployees"));
            })
        .hasMessageContainingAll("Error while processing statement: FAILED");
    // Check that lineage was still produced
    //        List<OpenLineage.RunEvent> emitted =
    // MockServerTestUtils.getEventsEmitted(mockServerClient);
    //        assertThat(emitted).size().isEqualTo(1);
    //
    // assertThat(emitted.get(0).getEventType()).isEqualTo(OpenLineage.RunEvent.EventType.FAIL);
  }

  @Test
  public void testSimpleCTAS() {
    createManagedHiveTable("employees", "id int, name string, team int");
    createManagedHiveTable("managers", "id int, name string, team int");
    createManagedHiveTable("teams", "id int, type int, building string");
    runHiveQuery(
        String.join(
            "\n",
            "create table result_t as",
            "select",
            "teams.type,",
            "teams.building,",
            "managers.name as manager,",
            "employees.name as employee",
            "from teams, managers, employees",
            "where teams.id = managers.team and teams.id = employees.team"));
    verifyEvents("simpleCtasComplete.json");
  }
}
