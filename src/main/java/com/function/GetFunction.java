package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
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
 * Azure Function to handle GET operations.
 */
public class GetFunction {

    // Response model for a person
    private static class Person {
        private final int id;
        private final String firstName;
        private final String lastName;
        private final String dateOfBirth;
        private final String residentialAddress;
        private final String contactNumber;

        public Person(int id, String firstName, String lastName, String dateOfBirth, String address, String contact) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.dateOfBirth = dateOfBirth;
            this.residentialAddress = address;
            this.contactNumber = contact;
        }
    }

    @FunctionName("GetPerson")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET},
            route = "person",
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger - Get Person(s)");

        String idParam = request.getQueryParameters().get("id");
        String dbUrl = System.getenv("MYSQL_CONNECTION_STRING");

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            Gson gson = new Gson();
            if (idParam != null && !idParam.isEmpty()) {
                // Get person by ID
                int id = Integer.parseInt(idParam);
                String sql = "SELECT * FROM persons WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Person person = new Person(
                                rs.getInt("id"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("date_of_birth"),
                                rs.getString("residential_address"),
                                rs.getString("contact_number")
                            );
                            return request.createResponseBuilder(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body(gson.toJson(person))
                                .build();
                        } else {
                            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                                .body("{\"message\": \"No record found with ID " + id + "\"}")
                                .build();
                        }
                    }
                }
            } else {
                // Get all persons
                String sql = "SELECT * FROM persons";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    List<Person> persons = new ArrayList<>();
                    while (rs.next()) {
                        persons.add(new Person(
                            rs.getInt("id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("date_of_birth"),
                            rs.getString("residential_address"),
                            rs.getString("contact_number")
                        ));
                    }
                    return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(persons))
                        .build();
                }
            }

        } catch (Exception e) {
            context.getLogger().severe("Database error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"message\": \"Internal server error.\"}")
                .build();
        }
    }
}
