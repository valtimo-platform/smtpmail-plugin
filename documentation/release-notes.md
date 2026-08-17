# Release notes

## 2.0.8

Valtimo bijgewerkt naar versie 13.41.0.

## 2.0.7

De optie Debug staat nu standaard uit; aanzetten had voorheen geen effect en zet nu het volledige mailverkeer, inclusief
Bcc-ontvangers en de inhoud van het bericht, in de logging - gebruik dit dus alleen tijdelijk bij het zoeken naar een
storing. Zijn er meerdere SMTP-configuraties, dan verstuurt een taak nu altijd via de configuratie die eraan gekoppeld
is in plaats van een willekeurige, en worden het onderwerp en de e-mailadressen vóór verzenden gecontroleerd. Adressen
op een hostnaam zonder punt, zoals een lokale mailcatcher (dev@localhost of test@mailhog), blijven daarbij toegestaan.

## 2.0.5

Herpublicatie zodat de plugin niet langer een vaste Valtimo-versie oplegt aan de applicatie die hem gebruikt.

## 2.0.4

Ondergebracht in een eigen repository met voorbeeldapplicatie, aparte documentatie en een PR-checks workflow. Verzenden zonder gebruikersnaam en wachtwoord werkt nu ook als deze velden als lege string zijn ingevuld.

## 1.0.2

Engelse en Duitse vertalingen toegevoegd; afzendernaam wordt nu meegestuurd in de MimeMessage.

## 1.0.1

SSL-properties correct doorgegeven aan de mailclient.

## 1.0.0

Eerste release van de SMTP mail plugin.
