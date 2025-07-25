package org.example.model;

import javafx.beans.property.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class Facture implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final IntegerProperty            id              = new SimpleIntegerProperty();
    private final IntegerProperty            prestataireId   = new SimpleIntegerProperty();
    private final StringProperty             description     = new SimpleStringProperty();
    private final ObjectProperty<LocalDate>  echeance        = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantHt       = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> tvaPct          = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantTva      = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> montantTtc      = new SimpleObjectProperty<>();
    private final BooleanProperty            paye            = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDate>  datePaiement    = new SimpleObjectProperty<>();
    private final BooleanProperty            preavisEnvoye   = new SimpleBooleanProperty();

    public Facture(int id,
                   int prestataireId,
                   String description,
                   LocalDate echeance,
                   BigDecimal montantHt,
                   BigDecimal tvaPct,
                   BigDecimal montantTva,
                   BigDecimal montantTtc,
                   boolean paye,
                   LocalDate datePaiement,
                   boolean preavisEnvoye) {

        this.id.set(id);
        this.prestataireId.set(prestataireId);
        this.description.set(description == null ? "" : description);
        this.echeance.set(echeance);
        this.montantHt.set(montantHt);
        this.tvaPct.set(tvaPct);
        this.montantTva.set(montantTva != null ? montantTva : calcTva(montantHt, tvaPct));
        this.montantTtc.set(montantTtc != null ? montantTtc : calcTtc(montantHt, tvaPct));
        this.paye.set(paye);
        this.datePaiement.set(datePaiement);
        this.preavisEnvoye.set(preavisEnvoye);
    }

    public int               getId()              { return id.get(); }
    public int               getPrestataireId()   { return prestataireId.get(); }
    public String            getDescription()     { return description.get(); }
    public LocalDate         getEcheance()        { return echeance.get(); }
    public BigDecimal        getMontantHt()       { return montantHt.get(); }
    public BigDecimal        getTvaPct()          { return tvaPct.get(); }
    public BigDecimal        getMontantTva()      { return montantTva.get(); }
    public BigDecimal        getMontantTtc()      { return montantTtc.get(); }
    public boolean           isPaye()             { return paye.get(); }
    public LocalDate         getDatePaiement()    { return datePaiement.get(); }
    public boolean           isPreavisEnvoye()    { return preavisEnvoye.get(); }
    public String            getEcheanceFr()      { return echeance.get()    == null ? "" : FR.format(echeance.get()); }
    public String            getDatePaiementFr()  { return datePaiement.get()== null ? "" : FR.format(datePaiement.get()); }

    public IntegerProperty            idProperty()            { return id; }
    public IntegerProperty            prestataireIdProperty() { return prestataireId; }
    public StringProperty             descriptionProperty()   { return description; }
    public ObjectProperty<LocalDate>  echeanceProperty()      { return echeance; }
    public ObjectProperty<BigDecimal> montantHtProperty()     { return montantHt; }
    public ObjectProperty<BigDecimal> tvaPctProperty()        { return tvaPct; }
    public ObjectProperty<BigDecimal> montantTvaProperty()    { return montantTva; }
    public ObjectProperty<BigDecimal> montantTtcProperty()    { return montantTtc; }
    public BooleanProperty            payeProperty()          { return paye; }
    public ObjectProperty<LocalDate>  datePaiementProperty()  { return datePaiement; }
    public BooleanProperty            preavisEnvoyeProperty() { return preavisEnvoye; }

    public static BigDecimal calcTva(BigDecimal ht, BigDecimal pct) {
        if (ht == null || pct == null) return BigDecimal.ZERO;
        return ht.multiply(pct).divide(BigDecimal.valueOf(100));
    }

    public static BigDecimal calcTtc(BigDecimal ht, BigDecimal pct) {
        return ht == null ? BigDecimal.ZERO : ht.add(calcTva(ht, pct));
    }
}
