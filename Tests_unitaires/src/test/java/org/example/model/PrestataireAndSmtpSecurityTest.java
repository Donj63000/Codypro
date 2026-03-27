package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrestataireAndSmtpSecurityTest {

    @Test
    void constructorTrimsTextFields() {
        Prestataire p = new Prestataire(
                1,
                "  Alpha  ",
                "  Societe  ",
                " 0102030405 ",
                " alpha@example.com ",
                50,
                " Conseil ",
                " Notes ",
                " 01/01/2024 "
        );

        assertEquals("Alpha", p.getNom());
        assertEquals("Societe", p.getSociete());
        assertEquals("0102030405", p.getTelephone());
        assertEquals("alpha@example.com", p.getEmail());
        assertEquals("Conseil", p.getFacturation());
        assertEquals("Notes", p.getServiceNotes());
        assertEquals("01/01/2024", p.getDateContrat());
    }

    @Test
    void constructorClampsLowNoteToZero() {
        Prestataire p = new Prestataire(1, "Alpha", "Soc", "0", "a@b.c", -10, "F", "N", "01/01/2024");

        assertEquals(0, p.getNote());
    }

    @Test
    void constructorClampsHighNoteToHundred() {
        Prestataire p = new Prestataire(1, "Alpha", "Soc", "0", "a@b.c", 150, "F", "N", "01/01/2024");

        assertEquals(100, p.getNote());
    }

    @Test
    void setNomTrimsAssignedValue() {
        Prestataire p = new Prestataire(1, "Alpha", "Soc", "0", "a@b.c", 10, "F", "N", "01/01/2024");

        p.setNom("  Beta  ");

        assertEquals("Beta", p.getNom());
    }

    @Test
    void setImpayesCannotGoBelowZero() {
        Prestataire p = new Prestataire(1, "Alpha", "Soc", "0", "a@b.c", 10, "F", "N", "01/01/2024");

        p.setImpayes(-5);

        assertEquals(0, p.getImpayes());
    }

    @Test
    void copyWithoutIdPreservesBusinessFields() {
        Prestataire p = new Prestataire(12, "Alpha", "Soc", "0", "a@b.c", 75, "Factu", "Notes", "01/01/2024");

        Prestataire copy = p.copyWithoutId();

        assertEquals(0, copy.getId());
        assertEquals(p.getNom(), copy.getNom());
        assertEquals(p.getFacturation(), copy.getFacturation());
        assertEquals(p.getServiceNotes(), copy.getServiceNotes());
        assertEquals(p.getDateContrat(), copy.getDateContrat());
    }

    @Test
    void getServiceTypeAliasesFacturation() {
        Prestataire p = new Prestataire(1, "Alpha", "Soc", "0", "a@b.c", 10, "Maintenance", "N", "01/01/2024");

        assertEquals(p.getFacturation(), p.getServiceType());
    }

    @Test
    void smtpSecurityFromNullDefaultsToStartTls() {
        assertEquals(SmtpSecurity.STARTTLS, SmtpSecurity.from(null));
    }

    @Test
    void smtpSecurityFromRecognizesNoneValues() {
        assertEquals(SmtpSecurity.NONE, SmtpSecurity.from("NONE"));
        assertEquals(SmtpSecurity.NONE, SmtpSecurity.from("AUCUN"));
    }

    @Test
    void smtpSecurityFromRecognizesSslAndFallsBackForUnknown() {
        assertEquals(SmtpSecurity.SSL, SmtpSecurity.from("SSL"));
        assertEquals(SmtpSecurity.SSL, SmtpSecurity.from("ssl/tls"));
        assertEquals(SmtpSecurity.STARTTLS, SmtpSecurity.from("unknown"));
    }
}
