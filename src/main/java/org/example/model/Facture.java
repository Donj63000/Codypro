package org.example.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Facture {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FR  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final IntegerProperty id     = new SimpleIntegerProperty();
    private final IntegerProperty prestataireId = new SimpleIntegerProperty();
    private final StringProperty  description   = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> echeance = new SimpleObjectProperty<>();
    private final DoubleProperty montant = new SimpleDoubleProperty();
    private final BooleanProperty paye   = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDate> datePaiement = new SimpleObjectProperty<>();

    /* ----- ctor ----- */
    public Facture(int id,int pid,String desc,LocalDate ech,double m,
                   boolean paye,LocalDate datePay) {
        this.id.set(id); prestataireId.set(pid); description.set(desc);
        echeance.set(ech); montant.set(m); this.paye.set(paye);
        datePaiement.set(datePay);
    }

    /* ----- getters (simplifiés) ----- */
    public int getId(){return id.get();}
    public int getPrestataireId(){return prestataireId.get();}
    public String getDescription(){return description.get();}
    public LocalDate getEcheance(){return echeance.get();}
    public double getMontant(){return montant.get();}
    public boolean isPaye(){return paye.get();}
    public LocalDate getDatePaiement(){return datePaiement.get();}
    public String getEcheanceFr() { return echeanceFr(); }
    public String getDatePaiementFr() { return datePaiementFr(); }

    /* ----- helper d’affichage ----- */
    public String echeanceFr(){ return FR.format(getEcheance()); }
    public String datePaiementFr(){
        return getDatePaiement()==null ? "" : FR.format(getDatePaiement());
    }
}
