package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(Function.class);

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

        public String getFirstname() { return firstname; }
        public String getLastname() { return lastname; }
        public int getStatusCode() { return StatusCode; }
        public String getMessage() { return Message; }
    }

    // Request model class
    private static class PersonRequest {
        private String firstName;
        private String lastName;
        private String dateOfBirth;
        private String residentialAddress;
        private String contactNumber;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

        public String getResidentialAddress() { return residentialAddress; }
        public void setResidentialAddress(String residentialAddress) { this.residentialAddress = residentialAddress; }

        public String getContactNumber() { return contactNumber; }
        public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    }

    @FunctionName("CreatePerson")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = { HttpMethod.POST },
            route = "person",
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        logger.info("Java HTTP trigger processed a POST request.");
        String requestBody = request.getBody().orElse("");
        Gson gson = new Gson();

        if (requestBody.isEmpty()) {
            return buildResponse(request, "", "", -1, "Unsuccessful - Request body is required.", HttpStatus.BAD_REQUEST);
        }

        try {
            PersonRequest personRequest = gson.fromJson(requestBody, PersonRequest.class);

            String firstName = personRequest.getFirstName() != null ? personRequest.getFirstName().trim() : null;
            String lastName = personRequest.getLastName() != null ? personRequest.getLastName().trim() : null;
            String dob = personRequest.getDateOfBirth() != null ? personRequest.getDateOfBirth().trim() : null;
            String address = personRequest.getResidentialAddress() != null ? personRequest.getResidentialAddress().trim() : null;
            String contact = personRequest.getContactNumber() != null ? personRequest.getContactNumber().trim() : null;

            // Null or empty checks
            if (firstName == null || firstName.isEmpty()) {
                return buildResponse(request, "", lastName != null ? lastName : "", -1, "Unsuccessful - First name is not entered.", HttpStatus.BAD_REQUEST);
            }
            if (lastName == null || lastName.isEmpty()) {
                return buildResponse(request, firstName, "", -1, "Unsuccessful - Last name is not entered.", HttpStatus.BAD_REQUEST);
            }
            if (dob == null || dob.isEmpty()) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Date of birth is not entered.", HttpStatus.BAD_REQUEST);
            }
            if (address == null || address.isEmpty()) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Address is not entered.", HttpStatus.BAD_REQUEST);
            }
            if (contact == null || contact.isEmpty()) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Contact number is not entered.", HttpStatus.BAD_REQUEST);
            }

            // Validation patterns
            String namePattern = "^[A-Za-z\\-'\\. ]{2,50}$";
            String phonePattern = "^[0-9+\\-() ]{7,20}$"; // basic format
            String datePattern = "^\\d{4}-\\d{2}-\\d{2}$"; // YYYY-MM-DD

            if (!firstName.matches(namePattern)) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - First name contains invalid characters.", HttpStatus.BAD_REQUEST);
            }
            if (!lastName.matches(namePattern)) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Last name contains invalid characters.", HttpStatus.BAD_REQUEST);
            }
            if (!Character.isUpperCase(firstName.charAt(0))) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - First name must start with a capital letter.", HttpStatus.BAD_REQUEST);
            }
            if (!Character.isUpperCase(lastName.charAt(0))) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Last name must start with a capital letter.", HttpStatus.BAD_REQUEST);
            }
            if (!dob.matches(datePattern)) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Date of birth must be in YYYY-MM-DD format.", HttpStatus.BAD_REQUEST);
            }
            if (!contact.matches(phonePattern)) {
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Contact number format is invalid.", HttpStatus.BAD_REQUEST);
            }

            // Insert into DB
            String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");

            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                String sql = "INSERT INTO persons (first_name, last_name, date_of_birth, residential_address, contact_number) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, firstName);
                    stmt.setString(2, lastName);
                    stmt.setString(3, dob);
                    stmt.setString(4, address);
                    stmt.setString(5, contact);

                    int rowsInserted = stmt.executeUpdate();
                    if (rowsInserted > 0) {
                        logger.info("Person inserted: {} {}", firstName, lastName);
                        return buildResponse(request, firstName, lastName, 0, "Success", HttpStatus.OK);
                    } else {
                        logger.error("Failed to insert person: {} {}", firstName, lastName);
                        return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Failed to insert into database.", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error: {}", e.getMessage());
                return buildResponse(request, firstName, lastName, -1, "Unsuccessful - Database error.", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            logger.error("Parsing error: {}", e.getMessage());
            return buildResponse(request, "", "", -1, "Unsuccessful - Invalid JSON format.", HttpStatus.BAD_REQUEST);
        }
    }

    private HttpResponseMessage buildResponse(HttpRequestMessage<?> request, String firstName, String lastName, int statusCode, String message, HttpStatus status) {
        Gson gson = new Gson();
        JsonResponse response = new JsonResponse(firstName, lastName, statusCode, message);
        return request.createResponseBuilder(status)
            .header("Content-Type", "application/json")
            .body(gson.toJson(response))
            .build();
    }
}
