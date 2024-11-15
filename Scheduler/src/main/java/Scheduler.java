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
import java.util.stream.Stream;
import java.util.stream.Collectors;


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
                row.add(object != null && !object.toString().isEmpty() ? object.toString() : null);
            }
            results.add(row);
        }

        return results;
    }

    public static void updateData(List<Game> games) throws Exception {
        Sheets sheet = new Sheets(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), authorize());

        //Sorting games based on time slot, ground for better updation in google sheets
        Comparator<Game> comparator = Comparator.comparing(game -> game.allotedTime);
        comparator = comparator.thenComparing(Comparator.comparing(game -> game.allotedGround));
        Stream<Game> sortedGamesStream = games.stream().sorted(comparator);
        games = sortedGamesStream.collect(Collectors.toList());

        try {
            List<List<Object>> writeData = new ArrayList<>();
            List<Object> dataHeader = new ArrayList<>();
            dataHeader.add("Time");
            dataHeader.add("Team 1");
            dataHeader.add("Team 2");
            dataHeader.add("Ground");
            dataHeader.add("UmpiringTeam1");
            dataHeader.add("UmpiringTeam2");
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
                dataRow.add(game.umpiringTeam2);
                dataRow.add(game.qualityScore);
                dataRow.add(game.teamWithDishonoredTicket);
                dataRow.add(game.team1NumGamesOnAllotedGround);
                dataRow.add(game.team2NumGamesOnAllotedGround);
                writeData.add(dataRow);
            }

            ValueRange vr = new ValueRange().setValues(writeData).setMajorDimension("ROWS");
            sheet.spreadsheets().values()
                    .update(spreadSheetId, "Output!A1:J", vr)
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
     * Assign umpiring duties to best result of games - works for 2 neutral umpires per game. All Divs RR model. Umpiring assigned with same div from a diff group
     */
    public static void assign2UmpiringDuties(List<Game> games, Map<String,Integer> umpiringTeamsMap, Map<String, Pair<String,String>> groupsMap) {
        for(Game game: games) {
            umpiringTeamsMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
            String division = groupsMap.get(game.team1).getValue();
            String group = groupsMap.get(game.team1).getKey();
            for(Map.Entry<String,Integer> entry : umpiringTeamsMap.entrySet()) {
                String uTeamName = entry.getKey();
                int remainingUmpiringCount = entry.getValue();
                if(remainingUmpiringCount > 0) {
                    String uTeamDivision = groupsMap.get(uTeamName).getValue();
                    String uTeamGroup = groupsMap.get(uTeamName).getKey();
                    if(uTeamDivision.equals(division) && !uTeamGroup.equals(group)) {
                        if(game.umpiringTeam1.isEmpty()) {
                            game.umpiringTeam1 = uTeamName;
                            remainingUmpiringCount--;
                        }
                        if(game.umpiringTeam2.isEmpty()) {
                            if(remainingUmpiringCount > 0) {
                                game.umpiringTeam2 = uTeamName;
                                remainingUmpiringCount--;
                            }
                        }
                        umpiringTeamsMap.put(uTeamName, remainingUmpiringCount);
                    }
                }
            }
        }
    }

    /**
     * Assign umpiring duties to best result of games - works for only 1 neutral umpire per game
     */
    public static boolean assignUmpiringDuties(List<Game> games, List<String> umpiringTeams, Map<String, Pair<String,String>> groupsMap) {
        //deep copy umpiringTeams as we are replacing the values in inplace
        List<String> localUmpiringTeamsCopy = new ArrayList<>();
        for(String team: umpiringTeams) {
            localUmpiringTeamsCopy.add(team);
        }
        //Sorting umpiring teams based on team name
        Collections.sort(localUmpiringTeamsCopy);

        //Sorting games based on ground, time slot for better umpiring assignment
        Comparator<Game> comparator = Comparator.comparing(game -> game.allotedGround);
        comparator = comparator.thenComparing(game -> game.allotedTime);
        Stream<Game> sortedGamesStream = games.stream().sorted(comparator);
        games = sortedGamesStream.collect(Collectors.toList());
        //printSchedule(games);

        return assignUmpiringDutiesBT(games, localUmpiringTeamsCopy, groupsMap, 0);
    }

    // A recursive utility function to assign umpiring duties - works for 1 umpire per game
    public static boolean assignUmpiringDutiesBT(List<Game> games, List<String> umpiringTeams, Map<String, Pair<String,String>> groupsMap, int gameIndex) {
        // Base case: If all umpiring duties are assigned then return true
        if(gameIndex >= umpiringTeams.size())
            return true;

        for(int i = 0; i < umpiringTeams.size(); i++) {
            //Use this umpiring team only if its not empty
            if(!umpiringTeams.get(i).isEmpty()) {
                String umpiringTeamGroup = groupsMap.get(umpiringTeams.get(i)).getKey();
                String playingTeam1Group = groupsMap.get(games.get(gameIndex).team1).getKey();
                String playingTeam2Group = groupsMap.get(games.get(gameIndex).team2).getKey();
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
     * Assign umpiring duties to best result of games - works for only 1 neutral umpire per game
     */
    public static boolean assignUmpiringDuties2(List<Game> games, Map<String,Integer> umpiringTeamsMap, Map<String, Pair<String,String>> groupsMap) {
        //deep copy umpiringTeams as we are replacing the values in inplace
        Map<String,Integer> localUmpiringTeamsMapCopy = new TreeMap<>(umpiringTeamsMap);

        //Sorting games based on ground, time slot for better umpiring assignment
        Comparator<Game> comparator = Comparator.comparing(game -> game.allotedGround);
        comparator = comparator.thenComparing(game -> game.allotedTime);
        Stream<Game> sortedGamesStream = games.stream().sorted(comparator);
        games = sortedGamesStream.collect(Collectors.toList());

        return assignUmpiringDutiesBT2(games, localUmpiringTeamsMapCopy, groupsMap, 0);
    }

    // A recursive utility function to assign umpiring duties - works for 2 umpires per game. Umpiring assigned from different Div
    public static boolean assignUmpiringDutiesBT2(List<Game> games, Map<String,Integer> umpiringTeamsMap, Map<String, Pair<String,String>> groupsMap, int gameIndex) {
        // Base case: If all umpiring duties are assigned then return true
        if(gameIndex >= games.size())
            return true;
        for(Map.Entry<String,Integer> entry : umpiringTeamsMap.entrySet()) {
            String umpiringTeam = entry.getKey();
            int remainingUmpiringsForThisTeam = entry.getValue();
            if(remainingUmpiringsForThisTeam > 0) {
                String umpiringTeamDiv = groupsMap.get(umpiringTeam).getValue();
                String playingTeam1Div = groupsMap.get(games.get(gameIndex).team1).getValue();
                String playingTeam2Div = groupsMap.get(games.get(gameIndex).team2).getValue();
                if(!umpiringTeamDiv.equals(playingTeam1Div) && !umpiringTeamDiv.equals(playingTeam2Div)) {
                    //both umpiring duties of the game are not assigned and this umpiring team has atleast 2 umpiring duties remaining
                    if(games.get(gameIndex).umpiringTeam1.isEmpty() &&  games.get(gameIndex).umpiringTeam2.isEmpty() && remainingUmpiringsForThisTeam >= 2) {
                        games.get(gameIndex).umpiringTeam1 = umpiringTeam;
                        games.get(gameIndex).umpiringTeam2 = umpiringTeam;
                        umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) - 2);
                        if (assignUmpiringDutiesBT2(games, umpiringTeamsMap, groupsMap,gameIndex + 1))
                            return true;
                        games.get(gameIndex).umpiringTeam1 = "";
                        games.get(gameIndex).umpiringTeam2 = "";
                        umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) + 2);
                    }
                    //Umpiring 1 for the game is assigned and 2nd umpiring isn't. This umpiring team has odd number of duties left
                    else if((games.get(gameIndex).umpiringTeam1.isEmpty() ||  games.get(gameIndex).umpiringTeam2.isEmpty()) && (remainingUmpiringsForThisTeam % 2) == 1) {
                        if(games.get(gameIndex).umpiringTeam1.isEmpty()) {
                            games.get(gameIndex).umpiringTeam1 = umpiringTeam;
                            umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) - 1);
                            if (assignUmpiringDutiesBT2(games, umpiringTeamsMap, groupsMap,gameIndex))
                                return true;
                            games.get(gameIndex).umpiringTeam1 = "";
                            umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) + 1);
                        }
                        else {
                            games.get(gameIndex).umpiringTeam2 = umpiringTeam;
                            umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) - 1);
                            if (assignUmpiringDutiesBT2(games, umpiringTeamsMap, groupsMap,gameIndex + 1))
                                return true;
                            games.get(gameIndex).umpiringTeam2 = "";
                            umpiringTeamsMap.put(umpiringTeam, umpiringTeamsMap.get(umpiringTeam) + 1);
                        }
                    }
                }
            }
        }

        //if an umpiring team cannot be assigned to any game, return false
        return false;
    }

    /**
     * Function to validate correct assignment of umpiring duties
     */
    public static boolean validateUmpiringAssignments(List<Game> games, List<String> umpiringTeams, Map<String, Pair<String,String>> groupsMap) {
        //Base case
        if(games.size() == 0 || groupsMap.size() == 0) {
            System.out.println("Games or Groups cannot be empty");
            return false;
        }

        //Create a map of umpiring count per team to check if same number of umpiring duties are assigned
        Map<String, Integer> umpiringCountMap = new HashMap<>();
        for(String team : umpiringTeams) {
            umpiringCountMap.put(team, umpiringCountMap.getOrDefault(team, 0) + 1);
        }
        System.out.println(umpiringCountMap);

        for(Game game: games) {
            if(groupsMap.get(game.umpiringTeam1).equals(groupsMap.get(game.team1)) || groupsMap.get(game.umpiringTeam1).equals(groupsMap.get(game.team2))) {
                System.out.println("Invalid Umpiring assignment for " + game.team1 + " vs " + game.team2 + " game. Umpiring assigned to " + game.umpiringTeam1);
                return false;
            }
            umpiringCountMap.put(game.umpiringTeam1, umpiringCountMap.get(game.umpiringTeam1) - 1);
        }
        System.out.println(umpiringCountMap);
        for(Map.Entry<String,Integer> entry : umpiringCountMap.entrySet()) {
            if(entry.getValue() != 0) {
                System.out.println("Invalid number of umpiring assignments for team "+entry.getKey());
                return false;
            }
        }

        return true;
    }

    /**
     * Function to validate correct assignment of umpiring duties
     */
    public static boolean validate2UmpiringAssignments(List<Game> games, Map<String, Pair<String,String>> groupsMap) {
        //Base case
        if(games.size() == 0 || groupsMap.size() == 0) {
            System.out.println("Games or Groups cannot be empty");
            return false;
        }

        for(Game game: games) {
            String playingTeam1 = game.team1;
            String umpiringTeam1 = game.umpiringTeam1;
            String umpiringTeam2 = game.umpiringTeam2;
            String playingTeam1Division = groupsMap.get(playingTeam1).getValue();
            String playingTeam1Group = groupsMap.get(playingTeam1).getKey();
            String umpiringTeam1Division = groupsMap.get(umpiringTeam1).getValue();
            String umpiringTeam1Group = groupsMap.get(umpiringTeam1).getKey();
            String umpiringTeam2Division = groupsMap.get(umpiringTeam2).getValue();
            String umpiringTeam2Group = groupsMap.get(umpiringTeam2).getKey();
            //For RR format
            //Return false if umpiring is assigned from diff division
            if(!playingTeam1Division.equals(umpiringTeam1Division) || !playingTeam1Division.equals(umpiringTeam2Division))
                return false;
            //Return false if umpiring is assigned from same group
            if(playingTeam1Group.equals(umpiringTeam1Group) || playingTeam1Group.equals(umpiringTeam2Group))
                return false;
        }
        return true;
    }

    /**
     * Function to validate correct assignment of umpiring duties
     */
    public static boolean validateUmpiringAssignments2(List<Game> games, Map<String,Integer> umpiringTeamsMap, Map<String, Pair<String,String>> groupsMap) {
        //Base case
        if(games.size() == 0 || groupsMap.size() == 0) {
            System.out.println("Games or Groups cannot be empty");
            return false;
        }
        Map<String,Integer> localUmpiringTeamsMapCopy = new TreeMap<>();

        for(Game game: games) {
            String playingTeam1 = game.team1;
            String playingTeam2 = game.team2;
            String umpiringTeam1 = game.umpiringTeam1;
            String umpiringTeam2 = game.umpiringTeam2;
            String playingTeam1Division = groupsMap.get(playingTeam1).getValue();
            String playingTeam2Division = groupsMap.get(playingTeam2).getValue();
            String umpiringTeam1Division = groupsMap.get(umpiringTeam1).getValue();
            String umpiringTeam2Division = groupsMap.get(umpiringTeam2).getValue();

            //For cross group gaming in Divs A & B and RR grouping in Div C
            if(playingTeam1Division.equals(umpiringTeam1Division) || playingTeam1Division.equals(umpiringTeam2Division) || playingTeam2Division.equals(umpiringTeam1Division) || playingTeam2Division.equals(umpiringTeam2Division))
                return false;
            localUmpiringTeamsMapCopy.put(umpiringTeam1,localUmpiringTeamsMapCopy.getOrDefault(umpiringTeam1,0)+1);
            localUmpiringTeamsMapCopy.put(umpiringTeam2,localUmpiringTeamsMapCopy.getOrDefault(umpiringTeam2,0)+1);
        }
        for(Map.Entry<String,Integer> entry : localUmpiringTeamsMapCopy.entrySet()) {
            int expectedCount = umpiringTeamsMap.get(entry.getKey());
            int assignedCount = entry.getValue();
            if(assignedCount != expectedCount) {
                System.out.println("Invalid umpiring assignment counts for Team: " + entry.getKey() + ". Expected: " + expectedCount + ", Assigned: " + assignedCount);
                return false;
            }
        }
        return true;
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

    public static void printSchedule(List<Game> bestResult) {
        System.out.println("--------------------------------------------------");
        for(Game game: bestResult) {
            System.out.println(game.allotedTime +","+game.allotedGround+", "+game.team1 +" vs "+game.team2+", umpiring: "+game.umpiringTeam1+", "+game.umpiringTeam2);
        }
        System.out.println("--------------------------------------------------");
    }

    static class Game {
        String team1;
        String team2;
        String umpiringTeam1 = "";
        String umpiringTeam2 = "";
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
        Map<String, Pair<String,String>> groupsMap = new HashMap<>();
        for (int i = 1; i < groups.size(); i++) {
            for(int j=0; j < groups.get(0).size(); j++){
                String division = groups.get(0).get(j).split("-")[0];
                String group = groups.get(0).get(j).split("-")[1];
                if(groups.get(i).get(j) != null)
                    groupsMap.put(groups.get(i).get(j), new Pair<>(group, division));
            }
        }
        System.out.println(groupsMap);
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
        //Collect Umpiring teams - Assuming 1 umpire per game
        List<String> umpiringTeams = new ArrayList<>();
        for (int i = 1; i < games.size(); i++) {
            umpiringTeams.add(games.get(i).get(3));
            if(games.get(i).size()>4)
                umpiringTeams.add(games.get(i).get(4));
        }

        //Collect Umpiring teams map - Assuming 2 umpires per game
        Map<String, Integer> umpiringTeamsMap = new HashMap<>();
        for (int i = 1; i < games.size(); i++) {
            umpiringTeamsMap.put(games.get(i).get(3), umpiringTeamsMap.getOrDefault(games.get(i).get(3),0) + 1);
            if(games.get(i).get(4) != null)
                umpiringTeamsMap.put(games.get(i).get(4), umpiringTeamsMap.getOrDefault(games.get(i).get(4),0) + 1);
        }

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
            //As we are changing the contents of actual entries, shallow copy doesn't work here. We need to do a deep copy
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
            /*
            //Assign single umpiring per game
            if (!assignUmpiringDuties(bestResults, umpiringTeams, groupsMap)) {
                System.out.println("Cannot assign umpiring duties");
                printSchedule(bestResults);
                return;
            }
            if(!validateUmpiringAssignments(bestResults, umpiringTeams, groupsMap)) {
                System.out.println("Invalid umpiring assignments");
                printSchedule(bestResults);
                return;
            }
            System.out.println("Umpiring assignment validation successful");
            */

            /*
            //Assign 2 umpirings per game - same division cross group umpiring
            assign2UmpiringDuties(bestResults, umpiringTeamsMap, groupsMap));
            if(!validate2UmpiringAssignments(bestResults, groupsMap)) {
                System.out.println("Invalid umpiring assignments");
                printSchedule(bestResults);
                return;
            }
            System.out.println("Umpiring assignment validation successful");
            */

            //Assign 2 umpirings per game - cross division umpiring
            if (!assignUmpiringDuties2(bestResults, umpiringTeamsMap, groupsMap)) {
                System.out.println("Cannot assign umpiring duties");
                printSchedule(bestResults);
                return;
            }
            System.out.println("Umpiring assignment successful");
            if(!validateUmpiringAssignments2(bestResults, umpiringTeamsMap, groupsMap)) {
                System.out.println("Invalid umpiring assignments");
                printSchedule(bestResults);
                return;
            }
            System.out.println("Umpiring assignment validation successful");

            updateData(bestResults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


