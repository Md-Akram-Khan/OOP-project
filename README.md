Electricity Billing System

Project Setup Instructions:

1. Download Required Files:

   ->  Download the project folder: "Electricity-Billing-System"
   
   ->  Download the required JAR files:
   
        + mysql-connector-java-8.0.28.jar
   
        + rs2xml.jar

3. Add JAR Files to the Project:
   
  ->  Open the project in NetBeans.
  
  ->  Locate the "Libraries" section in the project.
  
  ->  Add the two JAR files (mysql-connector-java-8.0.28.jar and rs2xml.jar) to the Libraries folder.
   
5. Prerequisites:
   
Ensure that your system has the following installed:

  ->  JDK (Java Development Kit)
  
  ->  MySQL Server
   
8. Set Up the MySQL Database
   
Open MySQL and execute the following commands one by one to create the required database and tables:

//-------------start--------------//

  ->  CREATE DATABASE ebs;
  
  ->  USE ebs;

  ->  CREATE TABLE login (
          meter_no VARCHAR(20),
          username VARCHAR(30),
          name VARCHAR(30),
          password VARCHAR(20),
          user VARCHAR(20)
      );

  ->  CREATE TABLE customer (
          name VARCHAR(20),
          meter_no VARCHAR(20),
          address VARCHAR(50),
          city VARCHAR(30),
          state VARCHAR(30),
          email VARCHAR(40),
          phone VARCHAR(20)
      );

  ->  CREATE TABLE meter_info (
          meter_no VARCHAR(20),
          meter_type VARCHAR(20),
          bill_type VARCHAR(20),
          days VARCHAR(20)
      );

  ->  CREATE TABLE tax (
          cost_per_unit VARCHAR(20),
          meter_rent VARCHAR(20),
          service_charge VARCHAR(20),
          service_tax VARCHAR(20),
          fixed_tax VARCHAR(20)
      );

  ->  INSERT INTO tax VALUES ('9', '47', '22', '57', '18');

  ->  CREATE TABLE bill (
          meter_no VARCHAR(20),
          month VARCHAR(30),
          units VARCHAR(20),
          totalbill VARCHAR(20),
          status VARCHAR(20)
      );
      
//-------------Finish--------------//


5. Configure Database Connection:
   
    ->Locate the conn.java file in the src folder.
   
    ->Open the file and update the MySQL username and password according to your MySQL credentials.
   
7. Run the Project:
   
    ->  Open the project.
   
    ->  Run the Main.java file.
   
    ->  Use the shortcut Shift + F6 to execute the program.
