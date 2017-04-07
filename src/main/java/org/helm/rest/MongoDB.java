/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.helm.rest;

import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.*;

import java.util.*;
import org.json.JSONObject;

/**
 *
 * @author tyuan
 */
public class MongoDB {
    MongoClient client;
    MongoDatabase db;
    public Exception error = null;
    
    public MongoDB(String connstring, String dbname) {
        try {
            MongoClientURI connectionString = new MongoClientURI(connstring);
            client = new MongoClient(connectionString);
            db = client.getDatabase(dbname);
        }
        catch (Exception e) {
            error = e;
        }
    }
    
    public boolean IsOpen() {
        return db != null;
    }
    
    public void Close() {        
        try {
            if (client != null) {
                client.close();
                client = null;
            }
        }
        catch (Exception e) {
            error = e;
        }

        db = null;
    }
    
    public JSONObject ListRules(int page, int countperpage, long id, String category) {
        BsonDocument q = new BsonDocument();
        if (id > 0)
            q.append("id", new BsonInt64(id));
        if (category != null && category.length() > 0)
            q.append("category", new BsonString(category));
        
        BsonDocument sort = new BsonDocument();
        sort.append("name", new BsonInt32(1));

        return List("HelmRules", "ID,category,name,description,author,CreatedDate", q, sort, page, countperpage);
    }
    
    public JSONObject ListMonomers(int page, int countperpage, long id, String polymertype, String monomertype, String symbol) {
        BsonDocument q = new BsonDocument();
        if (id > 0)
            q.append("id", new BsonInt64(id));
        if (polymertype != null && polymertype.length() > 0)
            q.append("polymertype", new BsonString(polymertype));
        if (monomertype != null && monomertype.length() > 0)
            q.append("monomertype", new BsonString(monomertype));
        if (symbol != null && symbol.length() > 0)
            q.append("monomertype", new BsonString(symbol));
        
        BsonDocument sort = new BsonDocument();
        sort.append("symbol", new BsonInt32(1));

        return List("HelmMonomers", "ID, Symbol, Name, NaturalAnalog, PolymerType, MonomerType, Author, CreatedDate, Status, R1, R2, R3, R4, R5", q, sort, page, countperpage);
    }
    
    public JSONObject LoadRule(long id) {
        BsonDocument q = new BsonDocument();
        if (id > 0)
            q.append("id", new BsonInt64(id));
    
        return LoadRow("HelmRules", q);
    }
    
    public JSONObject LoadMonomer(long id) {
        BsonDocument q = new BsonDocument();
        if (id > 0)
            q.append("id", new BsonInt64(id));

        return LoadRow("HelmMonomers", q);
    }
    
    public JSONObject LoadRow(String table, BsonDocument where) {
        MongoCollection coll = db.getCollection(table);
        MongoCursor cur = coll.find(where).limit(1).iterator();
        return Result2Json(cur);
    }
    
    public JSONObject List(String table, String cols, BsonDocument where, BsonDocument sortby, int page, int countperpage) {
        if (page < 1)
            page = 1;
        if (countperpage < 1)
            countperpage = 10;
        
        long count;
        FindIterable iter;
        MongoCollection coll = db.getCollection(table);
        if (where == null) {
            count = coll.count();
            iter = coll.find();
        }
        else {
            count = coll.count(where);
            iter = coll.find(where);
        }
        
        if (sortby != null)
            iter = iter.sort(sortby);
        
        if (cols != null) {
            String[] ss = cols.split(",");
            Document fields = new Document("_id", false);
            for (int i = 0; i < ss.length; ++i){
                fields.append(ss[i].trim().toLowerCase(), true);
            }
            iter = iter.projection(fields);
        }
        
        long mod = count % countperpage;
        long pages = (count - mod) / countperpage + (mod == 0 ? 0 : 1);
        
        if (page > 1)
            iter = iter.skip((page - 1) * countperpage);
        iter = iter.limit(countperpage);
        MongoCursor cur = iter.iterator();
        
        JSONObject ret = new JSONObject();
        ret.put("page", page);
        ret.put("pages", pages);
        ret.put("rows", ResultSet2Json(cur));
        
        cur.close();
        return ret;
    }
    
    public long SelectID(String table, String key, String value) {
        key = key.toLowerCase();
        
        MongoCollection coll = db.getCollection(table);
        BsonDocument where = new BsonDocument(key, new BsonString(value));
        Document fields = new Document("_id", false);
        fields.append("id", true);
        MongoCursor cur = coll.find(where).limit(1).projection(fields).iterator();        
        if (cur == null || !cur.hasNext())
            return 0;
        
        Document d = (Document)cur.next();
        return (long)d.get("id");
    }
    
    public long[] SelectList(String table, String key, BsonDocument where) {
        key = key.toLowerCase();
        
        MongoCollection coll = db.getCollection(table);
        Document fields = new Document("_id", false);
        fields.append(key, true);
        MongoCursor cur = (where == null ? coll.find() : coll.find(where)).projection(fields).iterator();
        if (cur == null || !cur.hasNext())
            return null;
    
        List<Long> list = new ArrayList();
        while (cur.hasNext()) {
            Document d = (Document)cur.next();
            list.add((long)d.get(key));
        }   
        
        long[] ret = new long[list.size()];
        for (int i = 0; i < ret.length; ++i)
            ret[i] = list.get(i);
        return ret;
    }
    
    public String SelectString(String table, String key, BsonDocument where) {
        key = key.toLowerCase();
        
        MongoCollection coll = db.getCollection(table);
        Document fields = new Document("_id", false);
        fields.append(key, true);
        MongoCursor cur = (where == null ? coll.find() : coll.find(where)).limit(1).projection(fields).iterator();
        if (cur == null || !cur.hasNext())
            return null;
        
        Document d = (Document)cur.next();
        return d.get(key) + "";
    }
    
    public ArrayList<JSONObject> ReadAsJson(String table) {
        return ReadAsJson(table, null, null);
    }
    
    public ArrayList<JSONObject> ReadAsJson(String table, String cols, BsonDocument where) {
        FindIterable iter;
        MongoCollection coll = db.getCollection(table);
        if (where == null) {
            iter = coll.find();
        }
        else {
            iter = coll.find(where);
        }
        
        if (iter != null) {
            String[] ss = cols.split(",");
            Document fields = new Document("_id", false);
            for (int i = 0; i < ss.length; ++i){
                fields.append(ss[i].trim().toLowerCase(), true);
            }
            iter = iter.projection(fields);
        }
        
        MongoCursor cur = iter.iterator();        
        if (cur == null || !cur.hasNext())
            return null;
        return ResultSet2Json(cur);
    }
    
    public String ReadAsSDF(String table, String molfilekey) {
        MongoCollection coll = db.getCollection(table);
        FindIterable iter = coll.find();
             
        String lb = System.getProperty("line.separator");
        molfilekey = molfilekey.toLowerCase();
        StringBuilder sb = new StringBuilder();
        
        if (iter == null) {
            return null;
        }
        else {
            Document fields = new Document("_id", false);
            iter = iter.projection(fields);
        }
        
        MongoCursor cur = iter.iterator();
        while (cur.hasNext()) {
            Document doc = (Document)cur.next();
            String m = doc.getString(molfilekey);
            if (m != null) {
                // the molfile from toolkit has extra $$$$ line
                // fix bug: https://github.com/PistoiaHELM/HELMWebEditor/issues/94
                int p = m.lastIndexOf("M  END") + 6;
                if (p > 6 && p < m.length() - 1)
                    m = m.substring(0, p);
            }
            else {
                m = lb + "   JSDraw203101711402D" + lb + lb + "  0  0  0  0  0  0              0 V2000" + lb + "M  END";
            }
            sb.append(m);
            sb.append(lb);     
            
            for (String k : doc.keySet()) {
                if (k.equals(molfilekey) || k.equals("_id"))
                    continue;

                sb.append("> <");
                sb.append(k);
                sb.append(">");
                sb.append(lb);

                String s = doc.get(k) + "";
                sb.append(s == null ? "" : s);
                sb.append(lb);

                sb.append(lb);
            }
            sb.append("$$$$");
            sb.append(lb);              
        }

        return sb.toString();
    }
    
    ArrayList<JSONObject> ResultSet2Json(MongoCursor rs) { 
        ArrayList<JSONObject> list = new ArrayList();
        if (rs == null || !rs.hasNext())
            return list;

        try {
            while (rs.hasNext()) {
                list.add(Result2Json(rs));
            }
        }
        catch (Exception e) {
            error = e;
            return null;
        }
        return list;
    }    
    
    JSONObject Result2Json(MongoCursor rs) {
        JSONObject ret = new JSONObject();
        if (rs == null || !rs.hasNext())
            return ret;

        try {
            Document doc = (Document)rs.next();
            for (String k : doc.keySet()) {
                ret.put(k.toLowerCase(), doc.get(k));
            }
        }
        catch (Exception e) {        
            error = e;
            return null;
        }
        return ret;
    }
    
    public JSONObject DelRecord(String table, long id) {
        BsonDocument where = new BsonDocument("id", new BsonInt64(id));
        MongoCollection coll = db.getCollection(table);
        coll.deleteMany(where);
        return null; 
    }
    
    long GetMaxID(String table) {
        MongoCollection coll = db.getCollection(table);
        
        Document fields = new Document("_id", false);
        fields.append("id", true);
        MongoCursor cur = coll.find().projection(fields).iterator();
        if (cur == null || !cur.hasNext())
            return 0;
        
        long id = 0;
        while (cur.hasNext()) {
            Document d = (Document)cur.next();
            Object t = d.get("id");
            if (t == null)
                continue;
            long i = (long)t;
            if (i > id)
                id = i;
        }
        return id;
    }
    
    public long SaveRecord(String table, long id, Map<String, String> data) {
        MongoCollection coll = db.getCollection(table);
        
        Document doc = new Document();
        for (String k : data.keySet()) {
            doc.append(k, data.get(k));
        }
        
        if (id > 0) {
            Document where = new Document("id", id);
            coll.findOneAndUpdate(where, new Document("$set", doc));
        }
        else {
            id = GetMaxID(table) + 1;
            doc.append("id", id);
            coll.insertOne(doc);
        }

        return id; 
    }
}
