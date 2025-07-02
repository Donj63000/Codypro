package org.example.model;

import javafx.beans.property.*;

public class Prestataire {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty nom = new SimpleStringProperty();
    private final StringProperty societe = new SimpleStringProperty();
    private final StringProperty telephone = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final IntegerProperty note = new SimpleIntegerProperty();
    private final StringProperty facturation = new SimpleStringProperty();
    private final StringProperty dateContrat = new SimpleStringProperty();

    public Prestataire(int id, String n, String s, String tel, String mail, int note, String fact, String date) {
        this.id.set(id);
        nom.set(n);
        societe.set(s);
        telephone.set(tel);
        email.set(mail);
        this.note.set(note);
        facturation.set(fact);
        dateContrat.set(date);
    }

    public int getId() { return id.get(); }
    public String getNom() { return nom.get(); }
    public void setNom(String s) { nom.set(s); }
    public String getSociete() { return societe.get(); }
    public String getTelephone() { return telephone.get(); }
    public String getEmail() { return email.get(); }
    public int getNote() { return note.get(); }
    public String getFacturation() { return facturation.get(); }
    public String getDateContrat() { return dateContrat.get(); }

    public IntegerProperty idProperty() { return id; }
    public StringProperty nomProperty() { return nom; }
    public StringProperty societeProperty() { return societe; }
    public StringProperty telephoneProperty() { return telephone; }
    public StringProperty emailProperty() { return email; }
    public IntegerProperty noteProperty() { return note; }
    public StringProperty facturationProperty() { return facturation; }
    public StringProperty dateContratProperty() { return dateContrat; }

    public Prestataire copyWithoutId() {
        return new Prestataire(0, getNom(), getSociete(), getTelephone(), getEmail(), getNote(), getFacturation(), getDateContrat());
    }
}
