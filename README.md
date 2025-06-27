# Azure Functions CRUD API (Java + MySQL)

This project implements a simple HTTP-triggered Azure Functions application written in **Java**, supporting basic **Create**, **Update**, and **Delete** operations on a **MySQL** database.

## Technologies Used

- Azure Functions (Java)
- MySQL Database
- Gson for JSON parsing
- Postman for testing
- Maven for dependency management

---

## Setup Instructions

### 1. Clone the Repository

git clone https://github.com/Kisara-de-Silva/Azure-Functions-Java01.git
cd Azure-Functions-Java01

### 2. Configure Environment Settings

{
  "IsEncrypted": false,
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "MYSQL_CONNECTION_STRING": "jdbc:mysql://localhost:3306/azurefuncdb1?user=root&password=yourpassword"
  }
}

### 3. Set up the Database

CREATE TABLE persons (
    id INT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    residential_address VARCHAR(255),
    contact_number VARCHAR(20)
);

### 4. Available Endpoints

i.) CREATE Person | POST | http://localhost:7071/api/HttpExample
{
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1990-01-01",
  "residentialAddress": "123 Main Street",
  "contactNumber": "+94771234567"
}

ii.) UPDATE Person | PUT | http://localhost:7071/api/UpdatePerson
{
  "id": 3,
  "firstName": "Michael",
  "lastName": "Scott",
  "dateOfBirth": "1975-03-15",
  "residentialAddress": "1725 Slough Avenue",
  "contactNumber": "+94771234567"
}

iii.) DELETE Person | DELETE | http://localhost:7071/api/DeletePerson
{
  "id": 3
}

iv.) GET Person | GET | http://localhost:7071/api/GetPerson


### 5. Start the Engine

--->> mvn clean package  
--->> mvn azure-functions:run

