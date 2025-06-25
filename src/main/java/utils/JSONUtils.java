package utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class JSONUtils {

    /**
     * Given the URL of a REST API, retrieves the JSON response from the API and returns it as a JSONObject
     *
     * @param url   the URL of the REST API
     * @return      the response from the API
     * @throws IOException      in case of errors while reading the response
     * @throws JSONException    in case of errors while parsing the response
     */
    public JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }

    /**
     * Given the URL of a REST API, retrieves the JSON response from the API and returns it as a JSONArray
     *
     * @param url   the URL of the REST API
     * @return      the response from the API
     * @throws IOException      in case of errors while reading the response
     * @throws JSONException    in case of errors while parsing the response
     */
    public JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONArray(jsonText);
        }
    }

    /**
     * Reads all content from a reader
     *
     * @param rd    the reader from which reading
     * @return      a string with the content of the reader
     * @throws IOException  in case of errors while reading from the reader
     */
    private String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}
