package com.ariyaan.Demo.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OsrmResponse { // Must be public to be accessed across packages
    public List<Route> routes;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route { // static inner class is fine, or move to its own file
        public double distance;
    }
}