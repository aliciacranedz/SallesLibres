package com.esiee.SallesLibres;

import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Autorise ton site HTML à communiquer avec ce serveur Java
public class SallesController {

    static class Evenement {
        ZonedDateTime debut;
        ZonedDateTime fin;
        List<String> salles = new ArrayList<>();
    }

    // Cette fonction s'active quand le site web demande l'URL : http://localhost:8080/api/salles?date=...
    @GetMapping("/salles")
    public List<String> getSallesLibres(@RequestParam String date) {
        
        // 👇 REMPLACE PAR TON VRAI LIEN ADE 👇
        String lienADE = "https://edt-consult.univ-eiffel.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=5480&projectId=1&calType=ical&nbWeeks=4&displayConfigId=8"; 
        
        try {
            // 1. On lit la date envoyée par le site web (au format YYYY-MM-DDTHH:mm)
            LocalDateTime dateSaisie = LocalDateTime.parse(date);
            ZonedDateTime heureRecherche = dateSaisie.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));

            // 2. On télécharge le calendrier et on extrait les salles
            List<Evenement> tousLesEvenements = lireEvenementsDepuisURL(lienADE);
            Set<String> toutesLesSalles = extraireToutesLesSalles(tousLesEvenements);

            // 3. On calcule et on renvoie la liste directement au site web !
            return obtenirSallesLibres(tousLesEvenements, toutesLesSalles, heureRecherche);

        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            // S'il y a un problème, on renvoie une liste avec juste un message d'erreur
            return Collections.singletonList("Erreur lors de la lecture des données.");
        }
    }

    // --- TES FONCTIONS OUTILS ---
    private List<Evenement> lireEvenementsDepuisURL(String lien) throws Exception {
        List<Evenement> listeCours = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

        URL url = new URL(lien);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
            String ligne;
            Evenement coursActuel = null;

            while ((ligne = br.readLine()) != null) {
                if (ligne.startsWith("BEGIN:VEVENT")) {
                    coursActuel = new Evenement();
                } else if (ligne.startsWith("DTSTART:") && coursActuel != null) {
                    try { coursActuel.debut = ZonedDateTime.parse(ligne.substring(8).trim(), formatter); } catch (Exception e) {}
                } else if (ligne.startsWith("DTEND:") && coursActuel != null) {
                    try { coursActuel.fin = ZonedDateTime.parse(ligne.substring(6).trim(), formatter); } catch (Exception e) {}
                } else if (ligne.startsWith("LOCATION:") && coursActuel != null) {
                    String sallesStr = ligne.substring(9).trim();
                    if (!sallesStr.isEmpty()) {
                        sallesStr = sallesStr.replace("\\,", ",");
                        String[] salles = sallesStr.split(",");
                        for (String salle : salles) {
                            String nomSalle = salle.trim();
                            if (!nomSalle.isEmpty()) { coursActuel.salles.add(nomSalle); }
                        }
                    }
                } else if (ligne.startsWith("END:VEVENT") && coursActuel != null) {
                    if (coursActuel.debut != null && coursActuel.fin != null && !coursActuel.salles.isEmpty()) {
                        listeCours.add(coursActuel);
                    }
                    coursActuel = null;
                }
            }
        }
        return listeCours;
    }

    private Set<String> extraireToutesLesSalles(List<Evenement> evenements) {
        Set<String> toutesLesSalles = new HashSet<>();
        for (Evenement e : evenements) {
            toutesLesSalles.addAll(e.salles);
        }
        return toutesLesSalles;
    }

    private List<String> obtenirSallesLibres(List<Evenement> evenements, Set<String> toutesLesSalles, ZonedDateTime heureRecherche) {
        Set<String> sallesOccupees = new HashSet<>();
        for (Evenement cours : evenements) {
            if (!heureRecherche.isBefore(cours.debut) && heureRecherche.isBefore(cours.fin)) {
                sallesOccupees.addAll(cours.salles);
            }
        }
        List<String> sallesLibres = new ArrayList<>();
        for (String salle : toutesLesSalles) {
            if (!sallesOccupees.contains(salle)) { sallesLibres.add(salle); }
        }
        Collections.sort(sallesLibres); // On trie par ordre alphabétique
        return sallesLibres;
    }
}