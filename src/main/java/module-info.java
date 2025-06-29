module pl.umcs.oop.demo2 {
    requires java.desktop;
    requires java.sql;


    opens pl.umcs.oop.demo2 to javafx.fxml;
    exports pl.umcs.oop.demo2;
}