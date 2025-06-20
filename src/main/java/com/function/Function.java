package com.function;

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
        
        if (requestBody.isEmpty()) {
            JsonResponse response = new JsonResponse("Request body is required");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(new Gson().toJson(response))
                    .build();
        }

        try {
            // Parse JSON manually
            Gson gson = new Gson();
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

            // Create response message
            String message = "Hello, " + personRequest.getFirstName().trim() + " " + personRequest.getLastName().trim();
            JsonResponse response = new JsonResponse(message);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Error parsing JSON: " + e.getMessage());
            JsonResponse response = new JsonResponse("Invalid JSON format");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(new Gson().toJson(response))
                    .build();
        }
    }
}
