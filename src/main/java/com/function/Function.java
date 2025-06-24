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

    // Custom response structure for success and failure
    private static class JsonResponse {
        private final String firstname;
        private final String lastname;
        private final int StatusCode;
        private final String Message;

        public JsonResponse(String firstname, String lastname, int statusCode, String message) {
            this.firstname = firstname;
            this.lastname = lastname;
            this.StatusCode = statusCode;
            this.Message = message;
        }

        public String getFirstname() {
            return firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public int getStatusCode() {
            return StatusCode;
        }

        public String getMessage() {
            return Message;
        }
    }

    // Request model class
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
                    methods = { HttpMethod.POST },
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a POST request.");

        // Get the request body as string
        String requestBody = request.getBody().orElse("");
        context.getLogger().info("Raw request body: " + requestBody);

        Gson gson = new Gson();

        if (requestBody.isEmpty()) {
            return buildResponse(request, "", "", -1, "Request body is required", HttpStatus.BAD_REQUEST);
        }

        try {
            // Parse JSON manually
            PersonRequest personRequest = gson.fromJson(requestBody, PersonRequest.class);

            // Validate parsed object and required fields
            if (personRequest == null ||
                personRequest.getFirstName() == null || personRequest.getFirstName().trim().isEmpty() ||
                personRequest.getLastName() == null || personRequest.getLastName().trim().isEmpty()) {
                return buildResponse(request, personRequest != null ? personRequest.getFirstName() : "",
                        personRequest != null ? personRequest.getLastName() : "",
                        -1, "Unsuccessful", HttpStatus.BAD_REQUEST);
            }

            String firstName = personRequest.getFirstName().trim();
            String lastName = personRequest.getLastName().trim();

            // Additional validation: Must only contain letters and be 2-50 chars
            if (!firstName.matches("^[A-Za-z]{2,50}$") || !lastName.matches("^[A-Za-z]{2,50}$")) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful", HttpStatus.BAD_REQUEST);
            }

            // Validation: First character must be uppercase
            if (!Character.isUpperCase(firstName.charAt(0)) || !Character.isUpperCase(lastName.charAt(0))) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful", HttpStatus.BAD_REQUEST);
            }

            // ✅ Connect to MySQL and insert data
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");

            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "INSERT INTO persons (first_name, last_name) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, firstName);
                    stmt.setString(2, lastName);

                    int rowsInserted = stmt.executeUpdate();

                    if (rowsInserted > 0) {
                        return buildResponse(request, firstName, lastName, 0, "Success", HttpStatus.OK);
                    } else {
                        return buildResponse(request, firstName, lastName, -1, "Unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
            } catch (SQLException e) {
                context.getLogger().severe("Database error: " + e.getMessage());
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            context.getLogger().severe("Error parsing JSON: " + e.getMessage());
            return buildResponse(request, "", "", -1, "Unsuccessful", HttpStatus.BAD_REQUEST);
        }
    }

    // Helper method to build response JSON consistently
    private HttpResponseMessage buildResponse(HttpRequestMessage<?> request, String firstName, String lastName, int statusCode, String message, HttpStatus status) {
        Gson gson = new Gson();
        JsonResponse response = new JsonResponse(firstName, lastName, statusCode, message);
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(response))
                .build();
    }
}
