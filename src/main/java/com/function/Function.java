package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import com.google.gson.Gson;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    private static class JsonResponse {
        private final String message;

        public JsonResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class PersonRequest {
        private String firstName;
        private String lastName;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    /**
     * This function listens at endpoint "/api/HttpExample". 
     * It accepts POST requests with JSON body containing firstName and lastName.
     * Example POST body: {"firstName": "John", "lastName": "Dawson"}
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a POST request.");

        // Get the request body as string
        String requestBody = request.getBody().orElse("");
        context.getLogger().info("Raw request body: " + requestBody);

        Gson gson = new Gson();

        if (requestBody.isEmpty()) {
            JsonResponse response = new JsonResponse("Request body is required");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();
        }

        try {
            // Parse JSON manually
            PersonRequest personRequest = gson.fromJson(requestBody, PersonRequest.class);

            // Validate the parsed object and required fields
            if (personRequest == null) {
                JsonResponse response = new JsonResponse("Invalid JSON structure");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }

            if (personRequest.getFirstName() == null || personRequest.getFirstName().trim().isEmpty() ||
                personRequest.getLastName() == null || personRequest.getLastName().trim().isEmpty()) {
                JsonResponse response = new JsonResponse("Both firstName and lastName are required");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }

            // ✅ Connect to MySQL and insert data
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");

            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "INSERT INTO persons (first_name, last_name) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, personRequest.getFirstName().trim());
                    stmt.setString(2, personRequest.getLastName().trim());

                    int rowsInserted = stmt.executeUpdate();

                    if (rowsInserted > 0) {
                        String message = "Saved to database: " + personRequest.getFirstName() + " " + personRequest.getLastName();
                        JsonResponse response = new JsonResponse(message);

                        return request.createResponseBuilder(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body(gson.toJson(response))
                                .build();
                    } else {
                        JsonResponse response = new JsonResponse("Failed to save to database");
                        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(gson.toJson(response))
                                .build();
                    }
                }
            } catch (SQLException e) {
                context.getLogger().severe("Database error: " + e.getMessage());
                JsonResponse response = new JsonResponse("Database connection failed");
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error parsing JSON: " + e.getMessage());
            JsonResponse response = new JsonResponse("Invalid JSON format");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(new Gson().toJson(response))
                    .build();
        }
    }

    private HttpResponseMessage createJsonResponse(HttpRequestMessage<?> request, HttpStatus status, String message) {
        Gson gson = new Gson();
        JsonResponse response = new JsonResponse(message);
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(response))
                .build();
    }
}
