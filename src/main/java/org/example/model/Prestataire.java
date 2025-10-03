package org.example.model;

import javafx.beans.property.*;
import java.io.Serializable;

public final class Prestataire implements Serializable {
    private static final long serialVersionUID = 1L;

    private final IntegerProperty id          = new SimpleIntegerProperty();
    private final StringProperty  nom         = new SimpleStringProperty();
    private final StringProperty  societe     = new SimpleStringProperty();
    private final StringProperty  telephone   = new SimpleStringProperty();
    private final StringProperty  email       = new SimpleStringProperty();
    private final IntegerProperty note        = new SimpleIntegerProperty();
    private final StringProperty  facturation   = new SimpleStringProperty();
    private final StringProperty  serviceNotes  = new SimpleStringProperty();
    private final StringProperty  dateContrat   = new SimpleStringProperty();
    private final IntegerProperty impayes     = new SimpleIntegerProperty();

    public Prestataire(int id,
                       String nom,
                       String societe,
                       String telephone,
                       String email,
                       int    note,
                       String facturation,
                       String serviceNotes,
                       String dateContrat) {

        this.id.set(id);
        this.nom.set(clean(nom));
        this.societe.set(clean(societe));
        this.telephone.set(clean(telephone));
        this.email.set(clean(email));
        this.note.set(clamp(note, 0, 100));
        this.facturation.set(clean(facturation));
        this.serviceNotes.set(clean(serviceNotes));
        this.dateContrat.set(clean(dateContrat));
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static int clamp(int v, int min, int max) { return Math.min(Math.max(v, min), max); }

    public int    getId()          { return id.get(); }
    public String getNom()         { return nom.get(); }
    public void   setNom(String v) { nom.set(clean(v)); }
    public String getSociete()     { return societe.get(); }
    public String getTelephone()   { return telephone.get(); }
    public String getEmail()       { return email.get(); }
    public int    getNote()        { return note.get(); }
    public String getFacturation() { return facturation.get(); }
    public String getServiceType()  { return getFacturation(); }
    public String getServiceNotes() { return serviceNotes.get(); }
    public String getDateContrat()  { return dateContrat.get(); }
    public int    getImpayes()     { return impayes.get(); }
    public void   setImpayes(int v){ impayes.set(Math.max(v, 0)); }

    public IntegerProperty idProperty()          { return id; }
    public StringProperty  nomProperty()         { return nom; }
    public StringProperty  societeProperty()     { return societe; }
    public StringProperty  telephoneProperty()   { return telephone; }
    public StringProperty  emailProperty()       { return email; }
    public IntegerProperty noteProperty()        { return note; }
    public StringProperty  facturationProperty()   { return facturation; }
    public StringProperty  serviceNotesProperty()  { return serviceNotes; }
    public StringProperty  dateContratProperty()   { return dateContrat; }
    public IntegerProperty impayesProperty()     { return impayes; }

    public Prestataire copyWithoutId() {
        return new Prestataire(0,
                getNom(),
                getSociete(),
                getTelephone(),
                getEmail(),
                getNote(),
                getFacturation(),
                getServiceNotes(),
                getDateContrat());
    }
}
