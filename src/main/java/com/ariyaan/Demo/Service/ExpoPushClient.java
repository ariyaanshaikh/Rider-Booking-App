package com.ariyaan.Demo.Service;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExpoPushClient {
    public record Result(String status,String id,String error) {}
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json=new ObjectMapper();
    private final String base,accessToken;
    public ExpoPushClient(@Value("${app.push.expo-url:https://exp.host/--/api/v2/push}") String base,
            @Value("${app.push.access-token:}") String accessToken) { this.base=base;this.accessToken=accessToken; }
    private JsonNode post(String path,Object body) {
        try {
            var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json").header("Accept","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (!accessToken.isBlank()) request.header("Authorization","Bearer "+accessToken);
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw new IllegalStateException("PUSH_HTTP_"+response.statusCode());
            JsonNode result=json.readTree(response.body());
            if(!result.has("data")) throw new IllegalStateException("PUSH_INVALID_RESPONSE");
            return result.path("data");
        } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException("PUSH_INTERRUPTED"); }
        catch(Exception ex) { throw new IllegalStateException("PUSH_UNAVAILABLE"); }
    }
    private Result result(JsonNode value) {
        return new Result(value.path("status").asText(),value.path("id").asText(),value.path("details").path("error").asText("UNKNOWN"));
    }
    public Result send(String token,Map<String,Object> data,int ttl) {
        String type=String.valueOf(data.get("type"));
        String title=switch(type) {
            case "RIDE_REQUEST" -> "New Ride Request";
            case "RIDE_ACCEPTED" -> "Rider accepted";
            case "PAYMENT_REQUIRED" -> "Payment required";
            case "RIDE_STARTED" -> "Ride started";
            case "RIDE_COMPLETED" -> "Ride Completed";
            default -> "Ride updated";
        };
        var response=post("/send",Map.of("to",token,"title",title,"body","Open the app for the latest ride details.",
                "data",data,"sound","default","priority","high","ttl",Math.max(1,Math.min(ttl,3600)),
                "channelId","RIDE_REQUEST".equals(type)?"ride-requests":"ride-status"));
        return result(response.isArray()?response.path(0):response);
    }
    public Result receipt(String id) {
        JsonNode value=post("/getReceipts",Map.of("ids",List.of(id))).path(id);
        return value.isMissingNode()?new Result("pending","",""):result(value);
    }
}
