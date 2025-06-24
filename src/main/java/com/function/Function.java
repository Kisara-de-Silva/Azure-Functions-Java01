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

    /**
     * Response structure with all required fields
     */
    private static class JsonResponse {
        private final String firstname;
        private final String lastname;
        private final int StatusCode;
        private final String Message;

        public JsonResponse(String firstname, String lastname, int StatusCode, String Message) {
            this.firstname = firstname;
            this.lastname = lastname;
            this.StatusCode = StatusCode;
            this.Message = Message;
        }

        public String getFirstname() { return firstname; }
        public String getLastname() { return lastname; }
        public int getStatusCode() { return StatusCode; }
        public String getMessage() { return Message; }
    }

    /**
     * Internal request model to represent incoming JSON
     */
    private static class PersonRequest {
        private String firstName;
        private String lastName;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
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

        // Get request body
        String requestBody = request.getBody().orElse("");
        context.getLogger().info("Raw request body: " + requestBody);

        Gson gson = new Gson();

        try {
            // Parse incoming JSON into model
            PersonRequest personRequest = gson.fromJson(requestBody, PersonRequest.class);

            // Validate input
            if (personRequest == null ||
                personRequest.getFirstName() == null || personRequest.getFirstName().trim().isEmpty() ||
                personRequest.getLastName() == null || personRequest.getLastName().trim().isEmpty()) {

                JsonResponse response = new JsonResponse(
                    personRequest != null ? personRequest.getFirstName() : "",
                    personRequest != null ? personRequest.getLastName() : "",
                    -1,
                    "Unsuccessful"
                );

                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }

            // Connect to MySQL and insert data
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");

            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "INSERT INTO persons (first_name, last_name) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, personRequest.getFirstName().trim());
                    stmt.setString(2, personRequest.getLastName().trim());

                    int rowsInserted = stmt.executeUpdate();

                    if (rowsInserted > 0) {
                        // Insert successful
                        JsonResponse response = new JsonResponse(
                            personRequest.getFirstName(),
                            personRequest.getLastName(),
                            0,
                            "Success"
                        );

                        return request.createResponseBuilder(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body(gson.toJson(response))
                                .build();
                    } else {
                        // Insert failed
                        JsonResponse response = new JsonResponse(
                            personRequest.getFirstName(),
                            personRequest.getLastName(),
                            -1,
                            "Unsuccessful"
                        );
                        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(gson.toJson(response))
                                .build();
                    }
                }
            } catch (SQLException e) {
                context.getLogger().severe("Database error: " + e.getMessage());
                JsonResponse response = new JsonResponse(
                    personRequest.getFirstName(),
                    personRequest.getLastName(),
                    -1,
                    "Unsuccessful"
                );
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error parsing JSON: " + e.getMessage());
            JsonResponse response = new JsonResponse("", "", -1, "Unsuccessful");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();
        }
    }
}
