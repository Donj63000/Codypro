package org.example.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.gui.ThemeManager;
import org.example.model.Prestataire;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class PDF {

    private PDF() {}

    public static void fiche(Stage owner, Prestataire p) {
        Path file = choose(owner, p.getNom() + "_fiche.pdf");
        if (file != null) {
            try {
                generateFiche(file, p);
                info("Fiche PDF exportee");
            } catch (Exception e) {
                error(e.getMessage());
            }
        }
    }

    public static void historiqueGlobal(Stage owner, DB dao) {
        Path file = choose(owner, "Historique_global_prestataires.pdf");
        if (file != null) {
            try {
                generateHistorique(file, dao);
                info("Historique PDF exportee");
            } catch (Exception e) {
                error(e.getMessage());
            }
        }
    }

    public static void generateFiche(Path file, Prestataire p) throws Exception {
        Files.createDirectories(file.getParent());
        try (Document doc = new Document();
             FileOutputStream out = new FileOutputStream(file.toFile())) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Fiche Prestataire - " + p.getNom(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Societe     : " + p.getSociete()));
            doc.add(new Paragraph("Telephone   : " + p.getTelephone()));
            doc.add(new Paragraph("Email       : " + p.getEmail()));
            doc.add(new Paragraph("Note        : " + p.getNote() + " %"));
            String type = p.getFacturation();
            doc.add(new Paragraph("Type de service : " + (type == null || type.isBlank() ? "Non renseigne" : type)));
            String notes = p.getServiceNotes();
            if (notes != null && !notes.isBlank()) {
                doc.add(new Paragraph("Description : " + notes));
            }
            doc.add(new Paragraph("Date contrat: " + p.getDateContrat()));
        }
    }

    // Adapter to match prompt.txt naming. Generates a fiche at given path.
    public static void exportFichePrestataire(Prestataire p, Path file) throws Exception {
        generateFiche(file, p);
    }

    public static void generateHistorique(Path file, DB dao) throws Exception {
        Files.createDirectories(file.getParent());
        try (Document doc = new Document();
             FileOutputStream out = new FileOutputStream(file.toFile())) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Historique des contrats et services",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph(" "));
            dao.list("").forEach(p ->
                    dao.services(p.getId()).forEach(sr -> {
                        try {
                            doc.add(new Paragraph(sr.date()
                                    + " - " + p.getNom()
                                    + " (" + p.getSociete() + ") : "
                                    + sr.desc()));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }));
        }
    }

    private static Path choose(Stage owner, String name) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        return Optional.ofNullable(fc.showSaveDialog(owner))
                .map(java.io.File::toPath)
                .orElse(null);
    }

    private static void info(String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    private static void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }
}
