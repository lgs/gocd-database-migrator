/*
 * Copyright 2020 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.dbsync;

import net.sf.dbdeploy.InMemory;
import net.sf.dbdeploy.database.syntax.DbmsSyntax;
import net.sf.dbdeploy.database.syntax.HsqlDbmsSyntax;
import net.sf.dbdeploy.exceptions.DbDeployException;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DbDeploySchemaMigrator {
    private final BasicDataSource sourceDataSource;
    private final Connection connection;

    public DbDeploySchemaMigrator(BasicDataSource sourceDataSource, Connection connection) {
        this.sourceDataSource = sourceDataSource;
        this.connection = connection;
    }

    public String migrationSQL() throws SQLException, DbDeployException, IOException, ClassNotFoundException {
        File deltas = Files.createTempDirectory("deltas").toFile();
        try {
            InMemory inMemory = new InMemory(sourceDataSource, dbms(), extractDeltas(deltas), "DDL");
            return inMemory.migrationSql();
        } finally {
            FileUtils.forceDelete(deltas);
        }
    }

    private File extractDeltas(File deltas) throws SQLException, IOException {
        String sourceDir = isH2() ? "/h2deltas/" : "/pgdeltas/";

        try (InputStream resourceAsStream = getClass().getResourceAsStream(sourceDir)) {
            List<String> fileNames = IOUtils.readLines(resourceAsStream, UTF_8);
            for (String fileName : fileNames) {
                FileUtils.copyURLToFile(getClass().getResource(sourceDir + fileName), new File(deltas, fileName));
            }
        }

        return deltas;
    }

    private DbmsSyntax dbms() throws SQLException {
        if (isH2()) {
            return new HsqlDbmsSyntax();
        }
        if (isPG()) {
            return new PostgreSQLDbmsSyntax();
        }
        throw new RuntimeException("Unsupported DB " + connection.getMetaData().getDatabaseProductName());
    }

    private boolean isPG() throws SQLException {
        return connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("PostgreSQL");
    }

    private boolean isH2() throws SQLException {
        return connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
    }

    public static class PostgreSQLDbmsSyntax extends DbmsSyntax {
        public PostgreSQLDbmsSyntax() {
        }

        public String generateTimestamp() {
            return "CURRENT_TIMESTAMP";
        }

        public String generateUser() {
            return "CURRENT_USER";
        }
    }
}
