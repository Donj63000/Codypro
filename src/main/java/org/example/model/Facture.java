package org.example.model;

import javafx.beans.property.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Facture implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FR  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final IntegerProperty id     = new SimpleIntegerProperty();
    private final IntegerProperty prestataireId = new SimpleIntegerProperty();
    private final StringProperty  description   = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> echeance = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantHt   = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> tvaPct      = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantTva  = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantTtc  = new SimpleObjectProperty<>();
    private final BooleanProperty paye   = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDate> datePaiement = new SimpleObjectProperty<>();
    private final BooleanProperty preavisEnvoye = new SimpleBooleanProperty();

    /* ----- ctor ----- */
    public Facture(int id,int pid,String desc,LocalDate ech,
                   BigDecimal montantHt, BigDecimal tvaPct,
                   BigDecimal montantTva, BigDecimal montantTtc,
                   boolean paye,LocalDate datePay, boolean preavis) {
        this.id.set(id);
        prestataireId.set(pid);
        description.set(desc);
        echeance.set(ech);
        this.montantHt.set(montantHt);
        this.tvaPct.set(tvaPct);
        this.montantTva.set(montantTva);
        this.montantTtc.set(montantTtc);
        this.paye.set(paye);
        datePaiement.set(datePay);
        preavisEnvoye.set(preavis);
    }

    /* ----- getters (simplifiés) ----- */
    public int getId(){return id.get();}
    public int getPrestataireId(){return prestataireId.get();}
    public String getDescription(){return description.get();}
    public LocalDate getEcheance(){return echeance.get();}
    public BigDecimal getMontantHt(){return montantHt.get();}
    public BigDecimal getTvaPct(){return tvaPct.get();}
    public BigDecimal getMontantTva(){return montantTva.get();}
    public BigDecimal getMontantTtc(){return montantTtc.get();}
    public boolean isPaye(){return paye.get();}
    public LocalDate getDatePaiement(){return datePaiement.get();}
    public boolean isPreavisEnvoye(){return preavisEnvoye.get();}
    public boolean preavisEnvoye(){return preavisEnvoye.get();}
    public String getEcheanceFr() { return echeanceFr(); }
    public String getDatePaiementFr() { return datePaiementFr(); }

    /* ----- helper d’affichage ----- */
    public String echeanceFr(){ return FR.format(getEcheance()); }
    public String datePaiementFr(){
        return getDatePaiement()==null ? "" : FR.format(getDatePaiement());
    }

    /* ----- calculs ----- */
    public static BigDecimal calcTva(BigDecimal ht, BigDecimal pct){
        return ht.multiply(pct).divide(BigDecimal.valueOf(100));
    }

    public static BigDecimal calcTtc(BigDecimal ht, BigDecimal pct){
        return ht.add(calcTva(ht,pct));
    }
}
