package org.example.dao;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider extends AutoCloseable {
    Connection getConnection() throws SQLException;

    @Override
    default void close() throws Exception {}
}
