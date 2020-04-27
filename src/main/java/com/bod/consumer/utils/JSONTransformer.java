package com.bod.consumer.utils;

import com.github.wnameless.json.flattener.JsonFlattener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Created by gbartolome on 5/22/16.
 */
public class JSONTransformer {

    private final Log LOG = LogFactory.getLog(JSONTransformer.class);
    private SimpleDateFormat dtc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String jsonstring;

    public JSONTransformer(String jsonstring) {
        this.jsonstring = jsonstring;
    }

    /*method validates if string is a JSON Object*/
    private static boolean isValidJSONObject(Object j) {

        try {
            new JSONObject(j.toString());
        } catch (JSONException jsonEx) {
            return false;
        }
        return true;
    }

    /*method validates if string is a JSON Array*/
    private static boolean isValidJSONArray(Object j) {

        try {
            new JSONArray(new JSONParser().parse(j.toString()).toString());
        } catch (ParseException jsonEx) {
            return false;
        } catch (JSONException jsonEx) {
            return false;
        }
        try {
            new JSONArray(j.toString());
        } catch (JSONException jsonEx) {
            return false;
        }
        return true;
    }

    public LinkedHashMap transform() {
        LinkedHashMap<String, String> jsonCSVSet = new LinkedHashMap<String, String>();
        try {
            ConfigProps config = new ConfigProps();
            ArrayList<String> headers = new ArrayList<String>(Arrays.asList(config.getPropValues("arrayheadersviper").split(",")));
            HashMap<String, String> jsonCSV = new HashMap<String, String>();
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(jsonstring);
            org.json.simple.JSONObject jobj = (org.json.simple.JSONObject) obj;

            Map<String, Object> map = new HashMap<>(JsonFlattener.flattenAsMap(jobj.toString()));
            map.entrySet().forEach(x -> jsonCSV.put(x.getKey(), x.getValue().toString()));
            /* inserts elements that only exist in headers, if element in
            JSON is empty put the header as key and blank as the value*/
            headers.stream().forEach(al -> {
                if (jsonCSV.containsKey(al)) {
                    jsonCSVSet.put(al.toLowerCase(), "\"" + jsonCSV.get(al) + "\"");
                } else if (!jsonCSVSet.containsKey(al)) {
                    if (al.equals("dtc")) {
                        jsonCSVSet.put(al.toLowerCase(), "\"" + dtc.format(new Date()) + "\"");
                    } else jsonCSVSet.put(al.toLowerCase(), "\"\"");
                }

            });
        } catch (Exception e) {
            LOG.error(e.getMessage());
            LOG.error(e.getCause());
            Arrays.stream(e.getStackTrace()).forEach(x -> LOG.error(x));
        }
        return jsonCSVSet;
    }

}
