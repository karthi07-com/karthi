import java.sql.*;

public class DB {

    static final String url = "jdbc:mysql://localhost:3306/busdb";
    static final String user = "root";          // change if you created another user
    static final String password = "";    

    public static Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}
