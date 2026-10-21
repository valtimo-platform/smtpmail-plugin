# Release notes

## 2.0.14

### Gewijzigd gedrag

Controleer bij het bijwerken of bestaande configuraties en proceslinks hier nog goed op aansluiten.

- **STARTTLS** - Met STARTTLS aangevinkt wordt de verbinding op poort 465 zoals voorheen direct versleuteld, en op elke
  andere poort (zoals 587) met STARTTLS. Biedt de server geen STARTTLS aan, dan wordt er niet meer onversleuteld
  verzonden maar mislukt het verzenden. Gebruikt de server direct TLS op een andere poort dan 465, kies dan protocol
  smtps.
- **Authenticatie** - Staat Authenticatie uit, dan worden gebruikersnaam en wachtwoord niet meer naar de server
  gestuurd. Zet Authenticatie aan bij een configuratie die toch moet inloggen.
- **Verplichte velden in 'Send mail'** - De actie 'Send mail' kan pas worden opgeslagen als ontvangers en onderwerp zijn
  ingevuld.
- **Time-out** - Een SMTP-server die niet reageert, laat de taak niet meer eindeloos wachten; het verzenden mislukt na
  een time-out.
- **Logging** - Het onderwerp van een verzonden e-mail komt niet meer in de logging.

### Verbeteringen

- Het veld Bijlagen accepteert nu ook één bestands-ID, of meerdere ID's gescheiden door een komma of puntkomma.
- Bijlagen houden hun eigen bestandsnaam en krijgen het juiste bestandstype mee, in plaats van een naam als
  'rapport.application/pdf'.
- Staat Authenticatie aan zonder gebruikersnaam of wachtwoord, dan zegt de foutmelding dat nu ook.
- Een ander protocol dan smtp of smtps geeft een duidelijke foutmelding.
- De gepubliceerde backend-module trekt geen publicatie-plugin meer mee als afhankelijkheid en is nu ook vanuit Maven te
  gebruiken.

### Opgeloste problemen

- Een uitgezet vinkje voor STARTTLS blijft nu uit wanneer een bestaande configuratie opnieuw wordt geopend en
  opgeslagen.
- Een leeggemaakt veld Bijlagen laat de actie 'Send mail' niet langer mislukken, ook in bestaande proceslinks.
- De Nederlandse en Engelse naam van de actie 'Send mail' zijn niet langer verwisseld.

## 2.0.13

De actie 'Send mail' kan de inhoud van de e-mail nu ook direct uit een tekst of procesvariabele halen, naast een
tijdelijk bestand. Zo kan dezelfde inhoud eerst in een gebruikerstaak bekeken worden met de e-mailvoorbeeldcomponent en
daarna verstuurd worden.

## 2.0.12

Een uitgezet vinkje voor Authenticatie blijft nu uit wanneer een bestaande configuratie opnieuw wordt geopend en
opgeslagen.

## 2.0.11

De velden Ontvangers, CC en BCC accepteren nu naast een lijst ook één tekstwaarde met meerdere adressen, gescheiden door
een komma of een puntkomma.

## 2.0.10

Ondersteuning voor Valtimo 13.48.0.

## 2.0.9

Een mail kan nu ook vanuit eigen code verstuurd worden, bijvoorbeeld vanuit een geplande taak, zonder dat daar een
lopend proces voor nodig is.

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
