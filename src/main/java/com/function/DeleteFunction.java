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
 * Azure Function to handle DELETE operations.
 */
public class DeleteFunction {

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

    // Request model
    private static class DeleteRequest {
        private int id;

        public int getId() {
            return id;
        }
    }

    @FunctionName("DeletePerson")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.DELETE},
            route = "person",
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger - Delete Person.");

        String requestBody = request.getBody().orElse("");
        Gson gson = new Gson();

        if (requestBody.isEmpty()) {
            return buildResponse(request, -1, "Request body is required.", HttpStatus.BAD_REQUEST);
        }

        try {
            DeleteRequest deleteRequest = gson.fromJson(requestBody, DeleteRequest.class);
            int id = deleteRequest.getId();

            if (id <= 0) {
                return buildResponse(request, -1, "Invalid or missing ID.", HttpStatus.BAD_REQUEST);
            }

            // ✅ Delete from database
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "DELETE FROM persons WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);

                    int rowsDeleted = stmt.executeUpdate();
                    if (rowsDeleted > 0) {
                        return buildResponse(request, 0, "Record deleted successfully.", HttpStatus.OK);
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

    // Helper method to build consistent responses
    private HttpResponseMessage buildResponse(HttpRequestMessage<?> request, int statusCode, String message, HttpStatus status) {
        Gson gson = new Gson();
        JsonResponse response = new JsonResponse(statusCode, message);
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(response))
                .build();
    }
}
