import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NACLUmpiringCheck {
    static Set<String> divAGroupA = new HashSet<>(Arrays.asList("Mighty Warriors","Fire Friends","Royal Knights","Knight Walkers (CA)","Astros+","BraveHearts","JerseyXI","Challengers CC"));
    static Set<String> divAGroupB = new HashSet<>(Arrays.asList("CSK Yorkers","Dream Killers","2point0","Dragons","Money Heist","Royal Kings","Singh Cricket Club","Deccan United CC"));
    static Set<String> divBGroupA = new HashSet<>(Arrays.asList("Seamers","Rising Stars","Kings X1","P@nthers XI","Breaking Bats","Titans","Daredevils","CricKings"));
    static Set<String> divBGroupB = new HashSet<>(Arrays.asList("Champions","Lords","Chargers","Unstoppable","Baazigar of Bay","Bleed Blue","Smoking Sadhoos","Skippers"));
    static Set<String> divCGroupA = new HashSet<>(Arrays.asList("Nexus XI","Tailenders","Raynor Royals","Mavericks","Avengers","Bay Tigers","IndianChamps","UC11"));
    static Set<String> divCGroupB = new HashSet<>(Arrays.asList("Barely XI","Alviso Warriors","Bay Boys","Centurions","Sunny","MINIONS","YAM CC-14","WCC Gladiators"));
    public static void main(String[] args) throws FileNotFoundException {
        Map<String, Set> map = new HashMap<>();
        for (String team: divAGroupA) {
            map.put(team, divAGroupA);
        }
        for (String team: divAGroupB) {
            map.put(team, divAGroupB);
        }
        for (String team: divBGroupA) {
            map.put(team, divBGroupA);
        }
        for (String team: divBGroupB) {
            map.put(team, divBGroupB);
        }
        for (String team: divCGroupA) {
            map.put(team, divCGroupA);
        }
        for (String team: divCGroupB) {
            map.put(team, divCGroupB);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("/Users/sreevik/Desktop/NACL/Spring_2024_RR.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fixtures = line.split("\\t");
                if(map.get(fixtures[0]) == map.get(fixtures[3]) || map.get(fixtures[1]) == map.get(fixtures[3]) || map.get(fixtures[0]) == map.get(fixtures[4]) || map.get(fixtures[1]) == map.get(fixtures[4])) {
                    System.out.println(line);
                }
            }
            System.out.println("All Good");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
