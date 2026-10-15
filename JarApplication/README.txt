Running the Electricity Billing System JAR
=========================================

Requirements: Java 17 or newer.

1. Make sure the Supabase database is set up (see ../FullProject/README.txt).

2. Create the .env file in the top-level project folder (copy .env.example and fill it in).
   The app looks for .env in the folder you run it from and up to three folders above it.

3. From this JarApplication folder, run:

     java -jar Electricity_Billing_System.jar

   Keep the lib folder next to the JAR. It holds the PostgreSQL driver.

Troubleshooting
---------------
* "Missing SUPABASE_DB_URL": the .env file wasn't found. Run the command from this folder,
  or set the three SUPABASE_* environment variables.
* Connection errors: check the host and password in Supabase > Project Settings > Database.
