package com.bod.consumer.utils;

import com.sun.rowset.CachedRowSetImpl;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Created by gbartolome on 1/21/16.
 */
public class AWSDBconnect {

    public void runSQL(String connectionstr, String sql) {
        try (Connection c = DriverManager.getConnection(connectionstr); Statement sqlstmt = c.createStatement()) {
            Class.forName("org.postgresql.Driver");
            CopyManager cm = new CopyManager((BaseConnection) c);
            sqlstmt.executeUpdate(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ResultSet sqlMapResult(String connectionstr, String sql) throws Exception {
        ConfigProps config = new ConfigProps();
        /*It is recommended that if you want to use temp tables, you should not call "prepareStatement". You can directly execute the query from the statement object.*/
        CachedRowSetImpl crs = new CachedRowSetImpl();
        try (Connection c = DriverManager.getConnection(connectionstr); Statement sqlstmt = c.createStatement(); ResultSet resultSet = sqlstmt.executeQuery(sql)) {
            Class.forName("org.postgresql.Driver");
            CopyManager cm = new CopyManager((BaseConnection) c);
            /*caches the result set since it will close one it's out of the class*/
            crs.populate(resultSet);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return crs;
    }
}
