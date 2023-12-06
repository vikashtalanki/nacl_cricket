import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import javafx.util.Pair;

import java.io.*;
import java.util.*;


public class Scheduler {

    static String spreadSheetId = "";


    private static Credential authorize() throws Exception {
        String currentFilePath = new File("").getAbsolutePath();
        String credentialLocationDir = currentFilePath+"/src/main/resources/";
        String credentialLocationFile = "credentials.json";

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new FileReader(credentialLocationDir+credentialLocationFile));

        List<String> scopes = Arrays.asList(SheetsScopes.SPREADSHEETS);

        GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow = new GoogleAuthorizationCodeFlow
                .Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(new File(credentialLocationDir)))
                .setAccessType("offline")
                .build();


        return new AuthorizationCodeInstalledApp(googleAuthorizationCodeFlow, new LocalServerReceiver()).authorize("user");
    }


    public static List<List<String>> getData(String sheetName) throws Exception {
        Sheets sheet = new Sheets(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), authorize());

        List<List<Object>> data = sheet.spreadsheets().values()
                .get(spreadSheetId, sheetName)
                .execute().getValues();

        List<List<String>> results = new ArrayList<>(data.size());
        for (List<Object> listObject : data) {
            List<String> row = new ArrayList<>(listObject.size());
            for (Object object : listObject) {
                row.add(object != null ? object.toString() : null);
            }
            results.add(row);
        }

        return results;
    }

    public static void updateData(List<Game> games) throws Exception {
        Sheets sheet = new Sheets(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), authorize());
        Collections.sort(games, (o1, o2) -> (o1.allotedTime.compareTo(o2.allotedTime)));

        try {
            List<List<Object>> writeData = new ArrayList<>();
            List<Object> dataHeader = new ArrayList<>();
            dataHeader.add("Time");
            dataHeader.add("Team 1");
            dataHeader.add("Team 2");
            dataHeader.add("Ground");
            dataHeader.add("UmpiringTeam1");
            dataHeader.add("Failure Score");
            dataHeader.add("Team with bad timing");
            dataHeader.add("Team 1 num completed games on ground");
            dataHeader.add("Team 2 num completed games on ground");
            writeData.add(dataHeader);

            for (Game game: games) {
                List<Object> dataRow = new ArrayList<>();
                dataRow.add(game.allotedTime);
                dataRow.add(game.team1);
                dataRow.add(game.team2);
                dataRow.add(game.allotedGround);
                dataRow.add(game.umpiringTeam1);
                dataRow.add(game.qualityScore);
                dataRow.add(game.teamWithDishonoredTicket);
                dataRow.add(game.team1NumGamesOnAllotedGround);
                dataRow.add(game.team2NumGamesOnAllotedGround);
                writeData.add(dataRow);
            }

            ValueRange vr = new ValueRange().setValues(writeData).setMajorDimension("ROWS");
            sheet.spreadsheets().values()
                    .update(spreadSheetId, "Output!A1:I", vr)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> findCommonBetweenTwoLists(List<String> list1, List<String> list2) {
        List<String> sortedList1 = new ArrayList<>(list1);
        List<String> sortedList2 = new ArrayList<>(list2);
        Collections.sort(sortedList1);
        Collections.sort(sortedList2);

        List<String> commonValues = new ArrayList<>();

        int i = 0, j = 0;
        while (i < sortedList1.size() && j < sortedList2.size()) {
            int cmp = sortedList1.get(i).compareTo(sortedList2.get(j));
            if (cmp == 0) {
                commonValues.add(sortedList1.get(i));
                i++;
                j++;
            } else if (cmp < 0) {
                i++;
            } else {
                j++;
            }
        }

        return commonValues;
    }

    /**
     * Given a team name this traverses the tickets to find all the open slots available for the team
     */
    public static List<String> findOpenSlotsForTeam(String teamName, List<List<String>> tickets) {
        List<String> teamAvailability = new ArrayList<>();

        for (int j = 1; j < tickets.size(); j++) {
            if (tickets.get(j).get(0).equals(teamName)) {
                for (int k = 1; k < tickets.get(j).size(); k++) {
                    if (!tickets.get(j).get(k).equals("N")) {
                        teamAvailability.add(tickets.get(0).get(k));
                    }
                }
            }
        }

        return teamAvailability;
    }

    /**
     * Assign umpiring duties to best result of games - works for only 1 neutral umpire per game
     */
    public static boolean assignUmpiringDuties(List<Game> games, List<String> umpiringTeams, Map<String, String> groupsMap) {
        return assignUmpiringDutiesBT(games, umpiringTeams, groupsMap, 0);
    }

    // A recursive utility function to assign umpiring duties
    public static boolean assignUmpiringDutiesBT(List<Game> games, List<String> umpiringTeams, Map<String, String> groupsMap, int gameIndex) {
        // Base case: If all umpiring duties are assigned then return true
        if(gameIndex >= umpiringTeams.size())
            return true;

        for(int i = 0; i < umpiringTeams.size(); i++) {
            //Use this umpiring team only if its not empty
            if(!umpiringTeams.get(i).isEmpty()) {
                String umpiringTeamGroup = groupsMap.get(umpiringTeams.get(i));
                String playingTeam1Group = groupsMap.get(games.get(gameIndex).team1);
                String playingTeam2Group = groupsMap.get(games.get(gameIndex).team2);
                //Check if this umpiring team is from different group as playing teams
                if(!umpiringTeamGroup.equals(playingTeam1Group) & !umpiringTeamGroup.equals(playingTeam2Group)) {
                    String umpiringTeam = umpiringTeams.get(i);
                    //Assign this umpiring team to this game
                    games.get(gameIndex).umpiringTeam1 = umpiringTeam;
                    umpiringTeams.set(i, "");
                    // Recur to assign rest of the umpiring teams
                    if(assignUmpiringDutiesBT(games, umpiringTeams, groupsMap, gameIndex + 1))
                        return true;
                    // If assigning umpiring team to game doesn't lead to a solution then remove umpiring team from game
                    games.get(gameIndex).umpiringTeam1 = "";//backtrack
                    umpiringTeams.set(i, umpiringTeam);
                }
            }
        }
        //if an umpiring team cannot be assigned to any game, return false
        return false;
    }

    /**
     * Given a time slot, it traverses grounds to find all possible grounds
     */
    public static List<Pair<Integer, Integer>> findOpenGroundsForSlot(String slot, List<List<String>> grounds) {
        List<Pair<Integer, Integer>> availableGrounds = new ArrayList<>();
        for (int p = 1; p < grounds.get(0).size(); p++) {
            if (grounds.get(0).get(p).equals(slot)) {
                for (int m = 1; m < grounds.size(); m++) {
                    if (grounds.get(m).get(p).equals("Y")) {
                        availableGrounds.add(new Pair<>(m, p));
                    }
                }
            }
        }
        return availableGrounds;
    }

    static class Game {
        String team1;
        String team2;
        String umpiringTeam1 = "";
        String allotedGround="";
        String allotedTime="";
        int qualityScore;
        String teamWithDishonoredTicket;
        int team1NumGamesOnAllotedGround=0;
        int team2NumGamesOnAllotedGround=0;

        List<String> team1Availability;
        List<String> team2Availability;

        int computeQualityScore(Map<String, Integer> priorityMap, Map<Pair<String, String>, Integer> groundPreferenceMap) {
            qualityScore = 0;
            teamWithDishonoredTicket = "NA";

            /** Lets first do time slot */
            // If no game scheduled lowest quality
            if (allotedTime.length() == 0) {
                qualityScore = 1000 * (priorityMap.get(team1) + priorityMap.get(team2));
            }
            // Team 1
            if (!team1Availability.contains(allotedTime)) {
                qualityScore = 1000 * priorityMap.get(team1);
                teamWithDishonoredTicket = team1;
            }
            // Team 2
            if (!team2Availability.contains(allotedTime)) {
                qualityScore = 1000 * priorityMap.get(team2);
                teamWithDishonoredTicket = team2;
            }

            /** Lets next do ground */
            for (Map.Entry<Pair<String, String>, Integer> entry : groundPreferenceMap.entrySet()) {
                if (entry.getKey().getKey().equals(team1) && entry.getKey().getValue().equals(allotedGround))
                    team1NumGamesOnAllotedGround = entry.getValue();
                else if (entry.getKey().getKey().equals(team2) && entry.getKey().getValue().equals(allotedGround))
                    team2NumGamesOnAllotedGround = entry.getValue();
            }
            qualityScore += 10 * Math.max(team1NumGamesOnAllotedGround, team2NumGamesOnAllotedGround);
            qualityScore += Math.min(team1NumGamesOnAllotedGround, team2NumGamesOnAllotedGround);
            return qualityScore;
        }

        List<List<String>> computeGroundSlots(List<List<String>> tickets, List<List<String>> grounds,
                                              Map<String, Integer> priorityMap, Map<Pair<String, String>, Integer> groundPreferenceMap) {

            // Find open time slots for Team 1
            team1Availability = findOpenSlotsForTeam(team1, tickets);
            // Find open time slots for Team 2
            team2Availability = findOpenSlotsForTeam(team2, tickets);

            // Find common time slot
            List<String> bestTimeSlots = findCommonBetweenTwoLists(team1Availability, team2Availability);

            // If no time slot then pick the time slot of the team with higher priority
            if (bestTimeSlots.size() == 0) {
                if (priorityMap.get(team1) > priorityMap.get(team2)) {
                    bestTimeSlots = team1Availability;
                } else if (priorityMap.get(team2) > priorityMap.get(team1)) {
                    bestTimeSlots = team2Availability;
                } else {
                    int coinToss = new Random().nextInt(2);
                    if (coinToss == 1) {
                        bestTimeSlots = team1Availability;
                    } else {
                        bestTimeSlots = team2Availability;
                    }
                }
            }

            // Compute all eligible open grounds for the time slots
            List<Pair<Integer, Integer>> eligibleGroundsIndices = new ArrayList<>();
            for (String bestTimeSlot : bestTimeSlots) {
                List<Pair<Integer, Integer>> groundsIndicesForBestTimeSlots = findOpenGroundsForSlot(bestTimeSlot, grounds);
                eligibleGroundsIndices.addAll(groundsIndicesForBestTimeSlots);
            }

            // No ground slots available
            if (eligibleGroundsIndices.size() == 0) {
                return grounds;
            }

            // Shuffle eligibleGroundsIndices
            int size = eligibleGroundsIndices.size();
            for (int i = 0; i < size - 1; i++) {
                int j = i + new Random().nextInt(size - i);
                eligibleGroundsIndices.set(i, eligibleGroundsIndices.set(j, eligibleGroundsIndices.get(i)));
            }

            Pair<Integer, Integer> bestSlot;
            int bestGroundQuality = 100000;
            Pair<Integer, Integer> winningSlot = null;
            int team1NumGamesOnCurrentGround = 0;
            int team2NumGamesOnCurrentGround = 0;

            // Pick the ground slot that matches the preference of either the two team
            // or the team with highest priority by basically picking the team with lowest
            // groundQuality
            for (Pair<Integer, Integer> eligibleGroundIndex : eligibleGroundsIndices) {
                Pair<Integer, Integer> currentSlot = eligibleGroundIndex;
                String currentGround = grounds.get(currentSlot.getKey()).get(0);
                int groundQuality = 0;
                for (Map.Entry<Pair<String, String>, Integer> entry : groundPreferenceMap.entrySet()) {
                    if (entry.getKey().getKey().equals(team1) && entry.getKey().getValue().equals(currentGround))
                        team1NumGamesOnCurrentGround = entry.getValue();
                    else if (entry.getKey().getKey().equals(team2) && entry.getKey().getValue().equals(currentGround))
                        team2NumGamesOnCurrentGround = entry.getValue();
                }
                groundQuality += 10 * Math.max(
                        team1NumGamesOnCurrentGround,
                        team2NumGamesOnCurrentGround
                );
                groundQuality += Math.min(
                        team1NumGamesOnCurrentGround,
                        team2NumGamesOnCurrentGround
                );

                // We have found a better ground!
                if (groundQuality <= bestGroundQuality) {
                    bestGroundQuality = groundQuality;
                    winningSlot = currentSlot;
                }
            }
            allotedGround = grounds.get(winningSlot.getKey()).get(0);
            allotedTime = grounds.get(0).get(winningSlot.getValue());
            grounds.get(winningSlot.getKey()).set(winningSlot.getValue(), "Taken");
            return grounds;
        }
    }

    public static void main(String[] args) {

        Properties prop = new Properties();
        try {
            prop.load(Scheduler.class.getResourceAsStream("nacl.properties"));
            spreadSheetId = prop.getProperty("NACL_SCHEDULER_SHEET_ID");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        //Refer https://medium.com/geekculture/how-to-read-data-from-google-sheets-ee335f787de6 for reading data from Google Sheets
        List<List<String>> tickets = null;
        List<List<String>> priority = null;
        List<List<String>> games = null;
        List<List<String>> grounds = null;
        List<List<String>> groundsHistory = null;
        List<List<String>> groups = null;

        // Read Groups
        try {
            groups = getData("Groups");
            if(groups.size() == 0) {
                System.out.println("Groups sheet doesn't have data");
                return;
            }
            System.out.println("Groups: Read " + (groups.size() - 1) + " groups");
        } catch (Exception e) {
            System.out.println("Cant read groups");
            e.printStackTrace();
            return;
        }
        System.out.println(groups);
        // Convert Groups into Map<Team Name, Group Name> for Easy Look Up
        Map<String, String> groupsMap = new HashMap<>();
        for (int i = 1; i < groups.size(); i++) {
            for(int j=0; j < groups.get(0).size(); j++){
                groupsMap.put(groups.get(i).get(j), groups.get(0).get(j));
            }
        }

        // Read Tickets
        try {
            tickets = getData("Tickets");
            if(tickets.size() == 0) {
                System.out.println("Tickets sheet doesn't have data");
                return;
            }
            System.out.println("Tickets: Read " + (tickets.size() - 1) + " tickets");
        } catch (Exception e) {
            System.out.println("Cant read tickets");
            e.printStackTrace();
            return;
        }
        System.out.println(tickets);

        // Read Priority
        try {
            priority = getData("Priority");
            if(priority.size() == 0) {
                System.out.println("Priority sheet doesn't have data");
                return;
            }
            System.out.println("Priority: Read " + (priority.size() - 1) + " priorities");
        } catch (Exception e) {
            System.out.println("Cant read priority");
            e.printStackTrace();
            return;
        }
        System.out.println(priority);
        // Convert Priority into Map for Easy Look Up
        Map<String, Integer> priorityMap = new HashMap<>();
        for (int i = 1; i < priority.size(); i++) {
            priorityMap.put(priority.get(i).get(0), Integer.parseInt(priority.get(i).get(1)));
        }
        // Lets confirm we have priority for every team
        for (int i = 1; i < tickets.size(); i++) {
            String teamName = tickets.get(i).get(0);
            if (!priorityMap.containsKey(teamName)) {
                System.out.println("No priority for " + teamName);
                return;
            }
        }

        // Read Games
        try {
            games = getData("Games");
            if(games.size() == 0) {
                System.out.println("Games sheet doesn't have data");
                return;
            }
            System.out.println("Games: Read " + (games.size() - 1) + " games");
        } catch (Exception e) {
            System.out.println("Cant read games");
            e.printStackTrace();
            return;
        }
        System.out.println(games);
        // Lets confirm we have priority for every team
        for (int i = 1; i < games.size(); i++) {
            String team1Name = games.get(i).get(1);
            if (!priorityMap.containsKey(team1Name)) {
                System.out.println("No priority for team game " + team1Name);
                return;
            }
            String team2Name = games.get(i).get(2);
            if (!priorityMap.containsKey(team2Name)) {
                System.out.println("No priority for team game " + team2Name + " " + team2Name.length());
                return;
            }
        }
        //Collect Umpiring teams - Assuming 1 umpiring duty per game
        List<String> umpiringTeams = new ArrayList<>();
        for (int i = 1; i < games.size(); i++) {
            umpiringTeams.add(games.get(i).get(3));
        }
        System.out.println(umpiringTeams);

        // Read Grounds
        try {
            grounds = getData("Grounds");
            if(grounds.size() == 0) {
                System.out.println("Grounds sheet doesn't have data");
                return;
            }
            System.out.println("Grounds: Read " + (grounds.size() - 1) + " grounds");
        } catch (Exception e) {
            System.out.println("Cant read grounds");
            e.printStackTrace();
            return;
        }
        System.out.println(grounds);
        for (int i = 1; i < tickets.get(0).size(); i++) {
            if (!tickets.get(0).get(i).equals(grounds.get(0).get(i))) {
                System.out.println("Miss match in header between grounds and tickets " + tickets.get(0).get(i).length() + " " + grounds.get(0).get(i).length());
                return;
            }
        }

        // Read Ground Preferences
        try {
            groundsHistory = getData("GroundHistory");
            if(grounds.size() == 0) {
                System.out.println("GroundsHistory sheet doesn't have data");
                return;
            }
            System.out.println("GroundsHistory: Read " + (groundsHistory.size() - 1) + " ground histories");
        } catch (Exception e) {
            System.out.println("Cant read GroundsHistory");
            e.printStackTrace();
            return;
        }
        System.out.println(groundsHistory);
        for (int i = 1; i < groundsHistory.size(); i++) {
            if (!tickets.get(i).get(0).equals(groundsHistory.get(i).get(0))) {
                System.out.println("Miss match in teams between tickets and ground preferences " + tickets.get(i).get(0).length() + " " + groundsHistory.get(i).get(0).length());
                return;
            }
        }
        for (int i = 1; i < groundsHistory.get(0).size(); i++) {
            if (!grounds.get(i).get(0).equals(groundsHistory.get(0).get(i))) {
                System.out.println("Miss match in grounds between grounds and ground preferences " + grounds.get(i).get(0).length() + " " + groundsHistory.get(0).get(i).length());
                return;
            }
        }
        // Convert groundsHistory into Map for Easy Look Up
        Map<Pair<String, String>, Integer> groundHistoryMap = new HashMap<>();
        for (int i = 1; i < groundsHistory.size(); i++) {
            String teamName = groundsHistory.get(i).get(0);
            for (int j = 1; j < groundsHistory.get(0).size(); j++) {
                String groundName = groundsHistory.get(0).get(j);
                groundHistoryMap.put(new Pair<>(teamName, groundName), 0);
                groundHistoryMap.put(new Pair<>(teamName, groundName), Integer.parseInt(groundsHistory.get(i).get(j)));
            }
        }

        List<Game> bestResults = new ArrayList<>();
        int numBestGamesScheduled = 0;
        int bestSchedulingQuality = 0;

        /*
         * Brute force iterations
         */
        for (int numIterations = 0; numIterations < 1000; numIterations++) {

            // Initialize
            //As we are changing the contents actual cells, shallow copy doesn't work here. We need to do a deep copy
            List<List<String>> groundsForIteration = new ArrayList<>();
            for(List<String> groundRow : grounds) {
                List<String> cloneGroundRow = new ArrayList<>();
                for(String groundCell : groundRow) {
                    cloneGroundRow.add(groundCell);
                }
                groundsForIteration.add(cloneGroundRow);
            }
            List<List<String>> gamesForIteration = new ArrayList<>(games);
            List<Game> results = new ArrayList<>();
            int numGamesScheduled = 0;
            int schedulingQuality = 0;

            // Randomize the order in which we schedule the games
            int size = gamesForIteration.size();
            for (int i = 1; i < size - 1; i++) {
                int j = i + new Random().nextInt(size - i);
                List<String> temp = gamesForIteration.get(i);
                gamesForIteration.set(i, gamesForIteration.get(j));
                gamesForIteration.set(j, temp);
            }
            /*
             * First row in the games file is just the column names we can skip and
             * so we start with i = 1
             */
            for (int i = 1; i < gamesForIteration.size(); i++) {

                // Ensure we have team names. Else exit
                if (gamesForIteration.get(i).get(1).isEmpty()) {
                    break;
                }

                Game g = new Game();
                g.team1 = gamesForIteration.get(i).get(1);
                g.team2 = gamesForIteration.get(i).get(2);

                // Find common slots for team1 and team 2
                groundsForIteration = g.computeGroundSlots(tickets, groundsForIteration, priorityMap, groundHistoryMap);

                if (!g.allotedGround.isEmpty()) {
                    numGamesScheduled += 1;
                }

                // Quality of the games schedule
                schedulingQuality += g.computeQualityScore(priorityMap, groundHistoryMap);
                results.add(g);
            }
            System.out.println("Iteration: " + numIterations + " Games scheduled " + numGamesScheduled + " Failure cost " + schedulingQuality);

            // Find the iteration with max number of games scheduled and minimum priority
            if ((numGamesScheduled > numBestGamesScheduled) || (numGamesScheduled == numBestGamesScheduled && schedulingQuality <= bestSchedulingQuality)) {
                numBestGamesScheduled = numGamesScheduled;
                bestResults = results;
                bestSchedulingQuality = schedulingQuality;
            }
        }

        // Print Details of Best Schedule
        System.out.println("*** BEST RESULT ****");
        System.out.println("Num games scheduled: " + numBestGamesScheduled);
        System.out.println("Quality Score: " + bestSchedulingQuality);

        try {
            if (assignUmpiringDuties(bestResults, umpiringTeams, groupsMap) == false) {
                System.out.print("Cannot assign umpiring duties");
                return;
            }
            System.out.println("Umpiring assignment successful");
            updateData(bestResults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


