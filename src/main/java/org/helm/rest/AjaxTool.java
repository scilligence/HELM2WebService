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
package org.helm.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.helm.chemtoolkit.AbstractChemistryManipulator;
import org.helm.notation2.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import com.mysql.jdbc.Connection;

@Path("/ajaxtool")
public class AjaxTool {
    Database db = null;
    
    @GET
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("/get")
    public Response CmdGet(@Context HttpServletRequest request) {
        db = new Database();
        Response  ret = null;
        if (!db.IsOpen()) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + (db.error == null ? "" : db.error.getMessage()))).build();
            return ret;
        }
        
        Map<String, String> args = getQueryParameters(request);
        try {
            ret = OnCmd(args.get("cmd"), args, request);
        } catch (Exception e) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
        
        db.Close();
        return ret;
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    @Path("/post")
    public Response CmdPost(@Context HttpServletRequest request) {
        db = new Database();        
        Response ret = null;
        if (!db.IsOpen()) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + (db.error == null ? "" : db.error.getMessage()))).build();
            return ret;
        }

        String cmd = getQueryParameters(request).get("cmd");
        Map<String, String> args = cmd.equals("openjsd") ? null : getFormParameters(request);
        try {
            ret = OnCmd(cmd, args, request);
        } catch (Exception e) {
            ret = Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
        
        db.Close();
        return ret;
    }

    Response OnCmd(String cmd, Map<String, String> items, HttpServletRequest request) throws Exception {
        JSONObject ret = new JSONObject();
        switch (cmd) {
            case "helm.toolkit.monomer.json": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                ret.put("list", ret2);
            }
            break;
            case "helm.toolkit.monomer.downloadjson": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            
            case "helm.monomer.del":
                ret = db.DelRecord("HelmMonomers", "ID=" + items.get("id"));
                break;
            case "helm.monomer.load":
                ret = db.LoadMonomer(ToLong(items.get("id")));
                break;
            case "helm.monomer.save": {
                Map<String, String> data = SelectData(items, "id,symbol,name,naturalanalog,molfile,smiles,polymertype,monomertype,r1,r2,r3,r4,r5,author".split(","));
                long id = ToLong(data.get("id"));
                String symbol = data.get("symbol");
                long tid = db.SelectID("select ID from HelmMonomers where Symbol=" + Database.SqlSafe(symbol));
                if (tid > 0 && tid != id)
                    throw new Exception("This symbol is used: " + symbol);
                id = db.SaveRecord("HelmMonomers", id, data);
                if (id > 0)
                    ret = db.ListMonomers(0, 0, id, null, null, null);
            }
            break;
            case "helm.monomer.suggest":
                break;
            case "helm.monomer.list": {
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String polymertype = items.get("polymertype");
                String monomertype = items.get("monomertype");
                String symbol = items.get("symbol");
                ret = db.ListMonomers(page, countperpage, 0, polymertype, monomertype, symbol);
            }
            break;
            case "helm.monomer.all": {
                ret.put("monomers", db.ReadAsJson("select * from HelmMonomers"));
            }            
            break;
            case "helm.monomer.json": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("select * from HelmMonomers");
                ret.put("list", ret2);
            }
            break;
            case "helm.monomer.downloadjson": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("select * from HelmMonomers");
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            case "helm.monomer.importfromtoolkit":
                ret = ImportFromToolkit();
                break;

            case "helm.rule.del":
                ret = db.DelRecord("HelmRules", "ID=" + items.get("id"));
                break;
            case "helm.rule.load":
                ret = db.LoadRule(ToLong(items.get("id")));
                break;
            case "helm.rule.save": {
                Map<String, String> data = SelectData(items, "id,category,name,description,script,author".split(","));
                long id = db.SaveRecord("HelmRules", ToLong(data.get("id")), data);
                if (id > 0)
                    ret = db.ListRules(0, 0, id, null);
            }
            break;
            case "helm.rule.list": {
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String category = items.get("category");
                ret = db.ListRules(page, countperpage, 0, category);
            }
            break;
            case "helm.rule.all": {
                ret.put("rules", db.ReadAsJson("select * from HelmRules"));
            }
            break;
            case "helm.rule.downloadjson":
            case "helm.rules.downloadjson": {
                ArrayList<JSONObject> ret2 = db.ReadAsJson("select * from HelmRules");
                String s = "org.helm.webeditor.RuleSet.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }

            case "openjsd": {
                ret = new JSONObject();
                Part part = request.getPart("file");
                String filename = getFileName(part);
                String contents = getValue(part);
                ret.put("filename", filename);
                ret.put("base64", EncodeBase64(contents));
                String s = "<html><head></head><body><textarea>" + wrapAjaxResult(ret) + "</textarea></body></html>";
                return Response.status(Response.Status.OK).entity(s).type("text/html").build();
            }
            case "savefile": {
                String filename = items.get("filename");
                String contents = items.get("contents");
                return Response
                        .ok(contents, "application/unknown")
                        .header("content-disposition", "attachment;filename=" + filename)
                        .build();
            }
            case "helm.properties":
                ret = CalculateProperties(items.get("helm"));
                break;
                
            case "cleanup":
                ret = Cleanup(items.get("input"), items.get("inputformat")); 
                break;

            default:
                return Response.status(Response.Status.OK).entity(wrapAjaxError("Unknown cmd: " + cmd)).build();
        }
        
        if (db != null && db.error != null) {
            if (ret == null)
                ret = new JSONObject();
            ret.put("dberror", db.error.getMessage());
        }
        return Response.status(Response.Status.OK).entity(wrapAjaxResult(ret)).build();
    }
    
    Map<String, String> SelectData(Map<String, String> items, String[] keys) {
        Map<String, String> ret = new HashMap<>();
        for (String k : keys) {
            if (items.containsKey(k))
                ret.put(k, items.get(k));
            else
                ret.put(k, null);
        }
        return ret;
    }
    
    JSONObject ImportFromToolkit() {
        int n = 0;
        try {
            //get monomer database via MonomerFacotry Singleton class
            Map<String, Map<String, Monomer>> allMonomers = MonomerFactory.getInstance().getMonomerDB();

            //loop through polymer type and monomer to build the JSON string
            Set<String> polymerTypes = allMonomers.keySet();
            for (String polymerType : polymerTypes) {
                //momomers for specific polymerType
                Map<String, Monomer> monomers = allMonomers.get(polymerType);
                Set<String> monomerIds = monomers.keySet();
                for (String monomerId : monomerIds) {
                    Monomer monomer = monomers.get(monomerId);
                    if (saveMonomer(monomer) > 0)
                        ++n;
                }
            }
        } catch (Exception e) {
        }    
        
        JSONObject ret = new JSONObject();
        ret.put("msg", n + " records imported");
        return ret;
    }
    
    ArrayList<JSONObject> getToolkitMonomers(){
        ArrayList<JSONObject> ret = new ArrayList<JSONObject>();
        
        try {
            //get monomer database via MonomerFacotry Singleton class
            Map<String, Map<String, Monomer>> allMonomers = MonomerFactory.getInstance().getMonomerDB();

            //loop through polymer type and monomer to build the JSON string
            Set<String> polymerTypes = allMonomers.keySet();
            for (String polymerType : polymerTypes) {
                //momomers for specific polymerType
                Map<String, Monomer> monomers = allMonomers.get(polymerType);
                Set<String> monomerIds = monomers.keySet();
                for (String monomerId : monomerIds) {
                    Monomer monomer = monomers.get(monomerId);
                    ret.add(monomer2Json(monomer));
                }
            }
        } catch (Exception e) {
        }
        
        return ret;
    }
    
    public static String EncodeBase64(String s) {
        if (s == null)
            return null;
        byte[] encodedBytes = Base64.encodeBase64(s.getBytes());
        return new String(encodedBytes);
    }
    
    public static String DecodeBase64(String s) {
        if (s == null)
            return null;
        byte[] decodedBytes = Base64.decodeBase64(s);
        return new String(decodedBytes);
    }
    
    long saveMonomer(Monomer m) {
        String symbol = m.getAlternateId();
        String sql = "select ID from HelmMonomers where upper(symbol)=" + Database.SqlSafe(symbol);
        if (db.SelectID(sql) > 0)
            return 0;
        
        Map<String, String> ret = new HashMap();
        ret.put("symbol", symbol);
        ret.put("name", m.getName());
        ret.put("naturalanalog", m.getNaturalAnalog());
        ret.put("molfile", m.getMolfile());
        ret.put("smiles", m.getCanSMILES());
        ret.put("polymertype", m.getPolymerType());
        ret.put("monomertype", m.getMonomerType());
        
        List<Attachment> al = m.getAttachmentList();
        List<String> l = new ArrayList();
        for (Attachment a : al) {
            String r = a.getLabel();
            ret.put(r, a.getCapGroupName());
        }
        return db.SaveRecord("HelmMonomers", 0, ret);
    }
    
    JSONObject monomer2Json(Monomer m) {
        JSONObject ret = new JSONObject();
        ret.put("id", m.getId());
        ret.put("symbol", m.getAlternateId());
        ret.put("name", m.getName());
        ret.put("naturalanalog", m.getNaturalAnalog());
        ret.put("molfile", m.getMolfile());
        ret.put("smiles", m.getCanSMILES());
        ret.put("polymertype", m.getPolymerType());
        ret.put("monomertype", m.getMonomerType());
        
        List<Attachment> al = m.getAttachmentList();
        List<String> l = new ArrayList();
        for (Attachment a : al) {
            ret.put(a.getLabel().toLowerCase(), a.getCapGroupName());
        }
        return ret;
    }
    
    static JSONObject Cleanup(String q, String inputformat) {
        if (StringUtils.isEmpty(q)) {
            return null;
        }
        
        JSONObject ret = new JSONObject();
        try {
            org.helm.chemtoolkit.cdk.CDKManipulator m = new org.helm.chemtoolkit.cdk.CDKManipulator();
            String molfile = null;
            if (inputformat != null && (inputformat.equals("mol") || inputformat.equals("molfile"))) {
                String smiles = m.convert(q, AbstractChemistryManipulator.StType.MOLFILE);
                molfile = m.convert(smiles, AbstractChemistryManipulator.StType.SMILES);
            }
            else {
                molfile = m.convert(q, AbstractChemistryManipulator.StType.SMILES);
            }
            ret.put("output", molfile);
        } catch (Exception e) {
        }
        return ret;
    }

    static JSONObject CalculateProperties(String helm) {
        if (StringUtils.isEmpty(helm)) {
            return null;
        }

        org.helm.notation2.tools.WebService webservice = new org.helm.notation2.tools.WebService();

        JSONObject ret = new JSONObject();
        try {
            ret.put("helm", helm);
            ret.put("mw", webservice.calculateMolecularWeight(helm));
            ret.put("mf", webservice.getMolecularFormula(helm));
            ret.put("ec", webservice.calculateExtinctionCoefficient(helm));
        } catch (Exception e) {
        }
        return ret;
    }

    static String getValue(Part part) throws IOException {
        if (part == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream(), "UTF-8"));
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[1024];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
            value.append(buffer, 0, length);
        }
        return value.toString();
    }

    static String GetTrace(Exception e) {
        StackTraceElement[] list = e.getStackTrace();
        if (list == null) {
            return null;
        }

        String s = "";
        for (int i = 0; i < list.length; ++i) {
            s += list[i].getFileName() + "->" + list[i].getClassName() + "->" + list[i].getMethodName() + ": line " + list[i].getLineNumber() + "|";
        }
        return s;
    }

    Map<String, String> getFormParameters(HttpServletRequest request) {
        Map<String, String> dict = new HashMap<>();
        Map<String, String> ret = new HashMap<>();
        String q = null;
        try {
            q = IOUtils.toString(request.getInputStream());
        } catch (Exception e) {
        }

        if (q != null && q.length() > 0) {
            dict = parseQueryString(q);
        }

        for (String k : dict.keySet()) {
            String v = dict.get(k);
            ret.put(k, v == null || v.isEmpty() ? null : v);
        }

        return ret;
    }
    
    private String getFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }
    
    Map<String, String> getQueryParameters(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return parseQueryString(queryString);
    }

    Map<String, String> parseQueryString(String queryString) {
        Map<String, String> queryParameters = new HashMap<>();
        if (StringUtils.isEmpty(queryString)) {
            return queryParameters;
        }

        String[] parameters = queryString.split("&");
        for (String parameter : parameters) {
            String[] keyValuePair = parameter.split("=");
            String v = keyValuePair.length < 2 ? null : keyValuePair[1];
            if (v != null) {
                try {
                    v = java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception e) {
                }
            }
            queryParameters.put(keyValuePair[0], v);
        }
        return queryParameters;
    }

    static int ToInt(String s) {
        try {
            if (s == null || s.length() == 0) {
                return 0;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    static long ToLong(String s) {
        try {
            if (s == null || s.length() == 0) {
                return 0;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
    
    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(JSONObject ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(java.util.ArrayList ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    public static String wrapAjaxError(String error) {
        JSONObject json = new JSONObject();
        json.put("succeed", false);
        json.put("error", error);
        return json.toString();
    }
}
