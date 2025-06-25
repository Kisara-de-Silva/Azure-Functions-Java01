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
 * Azure Function to handle UPDATE operations.
 */
public class UpdateFunction {

    // Custom response structure
    private static class JsonResponse {
        private final int StatusCode;
        private final String Message;

        public JsonResponse(int statusCode, String message) {
            this.StatusCode = statusCode;
            this.Message = message;
        }

        public int getStatusCode() {
            return StatusCode;
        }

        public String getMessage() {
            return Message;
        }
    }

    // Request model class for update
    private static class UpdateRequest {
        private int id;
        private String firstName;
        private String lastName;
        private String dateOfBirth;
        private String residentialAddress;
        private String contactNumber;

        public int getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getDateOfBirth() {
            return dateOfBirth;
        }

        public String getResidentialAddress() {
            return residentialAddress;
        }

        public String getContactNumber() {
            return contactNumber;
        }
    }

    @FunctionName("UpdatePerson")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.PUT},
                authLevel = AuthorizationLevel.ANONYMOUS) 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger - Update Person.");
        String requestBody = request.getBody().orElse("");
        Gson gson = new Gson();

        if (requestBody.isEmpty()) {
            return buildResponse(request, -1, "Request body is required.", HttpStatus.BAD_REQUEST);
        }

        try {
            UpdateRequest updateRequest = gson.fromJson(requestBody, UpdateRequest.class);

            // Validate required fields
            if (updateRequest.getId() <= 0) {
                return buildResponse(request, -1, "Invalid or missing ID.", HttpStatus.BAD_REQUEST);
            }
            if (updateRequest.getFirstName() == null || updateRequest.getFirstName().trim().isEmpty()) {
                return buildResponse(request, -1, "First name is required.", HttpStatus.BAD_REQUEST);
            }
            if (updateRequest.getLastName() == null || updateRequest.getLastName().trim().isEmpty()) {
                return buildResponse(request, -1, "Last name is required.", HttpStatus.BAD_REQUEST);
            }
            if (updateRequest.getContactNumber() == null || updateRequest.getContactNumber().length() < 10) {
                return buildResponse(request, -1, "Contact number must have at least 10 characters.", HttpStatus.BAD_REQUEST);
            }

            // Name pattern check
            String namePattern = "^[A-Za-z\\-'\\. ]{2,50}$";
            if (!updateRequest.getFirstName().matches(namePattern)) {
                return buildResponse(request, -1, "First name contains invalid characters.", HttpStatus.BAD_REQUEST);
            }
            if (!updateRequest.getLastName().matches(namePattern)) {
                return buildResponse(request, -1, "Last name contains invalid characters.", HttpStatus.BAD_REQUEST);
            }

            // Capitalization check
            if (!Character.isUpperCase(updateRequest.getFirstName().charAt(0))) {
                return buildResponse(request, -1, "First name must start with a capital letter.", HttpStatus.BAD_REQUEST);
            }
            if (!Character.isUpperCase(updateRequest.getLastName().charAt(0))) {
                return buildResponse(request, -1, "Last name must start with a capital letter.", HttpStatus.BAD_REQUEST);
            }

            // ✅ Update in database
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "UPDATE persons SET first_name = ?, last_name = ?, date_of_birth = ?, residential_address = ?, contact_number = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, updateRequest.getFirstName());
                    stmt.setString(2, updateRequest.getLastName());
                    stmt.setString(3, updateRequest.getDateOfBirth());
                    stmt.setString(4, updateRequest.getResidentialAddress());
                    stmt.setString(5, updateRequest.getContactNumber());
                    stmt.setInt(6, updateRequest.getId());

                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated > 0) {
                        return buildResponse(request, 0, "Record updated successfully.", HttpStatus.OK);
                    } else {
                        return buildResponse(request, -1, "No record found with the given ID.", HttpStatus.NOT_FOUND);
                    }
                }
            } catch (SQLException e) {
                context.getLogger().severe("Database error: " + e.getMessage());
                return buildResponse(request, -1, "Database error.", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            context.getLogger().severe("JSON parsing error: " + e.getMessage());
            return buildResponse(request, -1, "Invalid JSON format.", HttpStatus.BAD_REQUEST);
        }
    }

    // Build JSON response
    private HttpResponseMessage buildResponse(HttpRequestMessage<?> request, int statusCode, String message, HttpStatus status) {
        Gson gson = new Gson();
        JsonResponse response = new JsonResponse(statusCode, message);
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(response))
                .build();
    }
}
