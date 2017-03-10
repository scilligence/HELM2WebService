/**
 * *****************************************************************************
 * Copyright C 2015, The Pistoia Alliance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ****************************************************************************
 */

/**
 *
 * @author tony yuan at Scilligence
 * 
 */


/*
This is an example implementation of HELM Monomer Manager and HELM Ruleset Manager using MySql

Database scheme:

create table HelmMonomers
(
id bigint not null primary key AUTO_INCREMENT,
Symbol varchar(256) not null,
Name varchar(256) not null,
NaturalAnalog varchar(256),
SMILES text,
PolymerType varchar(256) not null,
MonomerType varchar(256),
Status varchar(256),
Molfile text,
Hashcode varchar(128),
R1 varchar(256),
R2 varchar(256),
R3 varchar(256),
R4 varchar(256),
R5 varchar(256),
Author nvarchar(256),
CreatedDate DateTime default CURRENT_TIMESTAMP
);

create table HELMRules
(
id bigint not null primary key AUTO_INCREMENT,
Category nvarchar(256),
Name nvarchar(512) not null,
Script text,
Description text,
Author nvarchar(256),
CreatedDate DateTime default CURRENT_TIMESTAMP
);

create unique index IX_HelmMonomers_Symbol on HelmMonomers(Symbol);

*/

package org.helm.rest;

import java.util.*;
import java.io.*;
import java.sql.*;

import org.json.JSONObject;

public class Database {
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    public Exception error = null;

    public Database() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://localhost/sys?user=sa&password=Scilligence01");
        }
        catch (Exception e) {
            error = e;
        }
    }
    
    public boolean IsOpen() {
        return conn != null;
    }
    
    public void Close() {
        try {
            if (rs != null) {
                rs.close();
                rs = null;
            }
        }
        catch (Exception e) {
            error = e;
        }
        
        try {
            if (stmt != null) {
                stmt.close();
                stmt = null;
            }
        }
        catch (Exception e) {
            error = e;
        }

        try {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        catch (Exception e) {
            error = e;
        }
    }
    
    public static String SqlSafe(String s) {
        return s == null ? "null" : "'" + s.replace("'", "''") + "'";
    }
    
    public JSONObject ListRules(int page, int countperpage, long id, String category) {
        String where = id > 0 ? "ID=" + id : "ID>0";
        if (category != null && category.length() > 0)
            where += " and category=" + SqlSafe(category);

        String sql = "select ID, category, name, description, author, CreatedDate" +
                " from HelmRules" + 
                " where " + where +
                " order by name";
        return List(sql, page, countperpage);
    }
    
    public JSONObject ListMonomers(int page, int countperpage, long id, String polymertype, String monomertype, String symbol) {
        String where = id > 0 ? "ID=" + id : "ID>0";
        if (polymertype != null && polymertype.length() > 0)
            where += " and PolymerType=" + SqlSafe(polymertype);
        if (monomertype != null && monomertype.length() > 0)
            where += " and MonomerType=" + SqlSafe(monomertype);
        if (symbol != null && symbol.length() > 0)
            where += " and (Symbol like " + SqlSafe(symbol + "%") + " or Name like " + SqlSafe(symbol + "%") + ")";
        
        String sql = "select ID, Symbol, Name, NaturalAnalog, PolymerType, MonomerType, Author, CreatedDate, Status, R1, R2, R3, R4, R5" +
                " from HelmMonomers" + 
                " where " + where +
                " order by Symbol";
        return List(sql, page, countperpage);
    }
    
    public JSONObject LoadRule(long id) {
        String sql = "select ID, category, name, script, description, author, CreatedDate" +
                " from HelmRules" + 
                " where ID=" + id;        
        return LoadRow(sql);
    }
    
    public JSONObject LoadMonomer(long id) {
        String sql = "SELECT ID, Symbol, Name, SMILES, NaturalAnalog, PolymerType, MonomerType, Author, CreatedDate, Status, R1, R2, R3, R4, R5, Molfile" +
            " from HelmMonomers" + 
            " where ID=" + id;
        return LoadRow(sql);
    }
    
    public JSONObject List(String sql, int page, int countperpage) {
        if (page < 1)
            page = 1;
        if (countperpage < 1)
            countperpage = 10;

        int count = 0;
        try {
            String sql2 = "select count(*) n from (" + sql + ") x";
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql2);
            if (rs.next())
                count = rs.getInt("n");
        }
        catch (Exception e) {
            error = e;
        }
        int mod = count % countperpage;
        int pages = (count - mod) / countperpage + (mod == 0 ? 0 : 1);
        
        sql += " limit " + ((page - 1) * countperpage) + "," + countperpage;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
        }
        catch (Exception e) {
            error = e;
            return null;
        }
        
        JSONObject ret = new JSONObject();
        ret.put("page", page);
        ret.put("pages", pages);
        ret.put("rows", ResultSet2Json(rs));
        return ret;
    }
    
    public long SelectID(String sql) {
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            if (rs.next())
                return rs.getLong(1);
        }
        catch (Exception e) {
            error = e;
        }    
        return 0;
    }
    
    public long[] SelectList(String sql) {
        List<Long> list = new ArrayList();
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next())
                list.add(rs.getLong(1));
        }
        catch (Exception e) {
            error = e;
        }    
        
        long[] ret = new long[list.size()];
        for (int i = 0; i < ret.length; ++i)
            ret[i] = list.get(i);
        return ret;
    }
    
    public String SelectString(String sql) {
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            if (rs.next())
                return rs.getString(1);
        }
        catch (Exception e) {
            error = e;
        }    
        return null;
    }
    
    public ArrayList<JSONObject> ReadAsJson(String sql) {
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
        }
        catch (Exception e) {
            error = e;
            return null;
        }    
        return ResultSet2Json(rs);      
    }
    
    public String ReadAsSDF(String sql, String molfilekey) {
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
        }
        catch (Exception e) {
            error = e;
            return null;
        }    
             
        String lb = System.getProperty("line.separator");
        molfilekey = molfilekey.toLowerCase();
        StringBuilder sb = new StringBuilder();
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            while (rs.next()) {
                String m = rs.getString(molfilekey);
                if (m != null) {
                    // the molfile from toolkit has extra $$$$ line
                    // fix bug: https://github.com/PistoiaHELM/HELMWebEditor/issues/94
                    int p = m.lastIndexOf("M  END") + 6;
                    if (p > 6 && p < m.length() - 1)
                        m = m.substring(0, p);
                }
                else {
                    m = "   JSDraw203101711402D" + lb + lb + "  0  0  0  0  0  0              0 V2000" + lb + "M  END";
                }
                
                sb.append(m);
                sb.append(lb);
                for (int i = 0; i < n; ++i) {
                    String k2 = meta.getColumnName(i + 1);
                    String k = k2.toLowerCase();
                    if (k.equals(molfilekey))
                        continue;
                    
                    sb.append("> <");
                    sb.append(k2);
                    sb.append(">");
                    sb.append(lb);
                    
                    String s = rs.getString(i + 1);
                    sb.append(s == null ? "" : s);
                    sb.append(lb);
                    
                    sb.append(lb);
                }
                    
                sb.append("$$$$");
                sb.append(lb);                    
            }
        }
        catch (Exception e) {
            error = e;
            return null;
        }
        return sb.toString();
    }
    
    ArrayList<JSONObject> ResultSet2Json(ResultSet rs) { 
        ArrayList<JSONObject> list = new ArrayList();
        try {
            while (rs.next()) {
                list.add(Result2Json(rs));
            }
        }
        catch (Exception e) {
            error = e;
            return null;
        }
        return list;
    }    
    
    JSONObject Result2Json(ResultSet rs) {
        JSONObject ret = new JSONObject();
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            for (int i = 0; i < n; ++i) {
                String k = meta.getColumnName(i + 1);
                //int type = meta.getColumnType(i + 1);
                ret.put(k.toLowerCase(), rs.getString(i + 1));
            }
        }
        catch (Exception e) {        
            error = e;
            return null;
        }
        return ret;
    }
    
    public JSONObject LoadRow(String sql) {
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);   
            if (rs.next())
                return Result2Json(rs);
        }
        catch (Exception e) {
            error = e;
            return null;
        }    
        return null;  
    }
    
    public JSONObject DelRecord(String table, String where) {
        String sql = "delete from " + table + " where " + where;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql); 
        }
        catch (Exception e) {
            error = e;
        }    
        return null; 
    }
    
    public long SaveRecord(String table, long id, Map<String, String> data) {
        String sql =  null;
        if (id > 0) {
            sql = "update " + table + " set ";
            int i = 0;
            for (String k : data.keySet()) {
                if (++i > 1)
                    sql += ",";
                String v = data.get(k);
                if (v == null)
                    sql += k + "=null";
                else
                    sql += k + "=" + SqlSafe(data.get(k));
            }
            sql += " where ID=" + id;
        }
        else {
            String cols = "";
            String vals = "";
            int i = 0;
            for (String k : data.keySet()) {
                if (++i > 1) {
                    cols += ",";
                    vals += ",";
                }
                cols += k;
                String v = data.get(k);
                if (v == null)
                    vals += "null";
                else
                    vals += SqlSafe(data.get(k));
            }
            sql = "insert into " + table + " (" + cols + ") values (" + vals + ")";
        }
        
        try {
            stmt = conn.createStatement();
            stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            if (id == 0) {
                rs = stmt.getGeneratedKeys();
                if (rs.next())
                    id = rs.getLong(1); 
            }
        }
        catch (Exception e) {
            error = e;
            return 0;
        }    

        return id; 
    }
}
