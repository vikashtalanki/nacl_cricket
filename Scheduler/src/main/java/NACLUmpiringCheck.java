import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NACLUmpiringCheck {
    static Set<String> divAGroupA = new HashSet<>(Arrays.asList("Royal Kings","JerseyXI","Kings X1","Knight Walkers (CA)","Money Heist","Mighty Warriors","CSK Yorkers","Royal Knights"));
    static Set<String> divAGroupB = new HashSet<>(Arrays.asList("2point0","Astros+","Dragons","Challengers CC","Dream Killers","Rising Stars","Seamers","Singh Cricket Club"));
    static Set<String> divBGroupA = new HashSet<>(Arrays.asList("Breaking Bats","UC11","Lords","Mavericks","Skippers","Smoking Sadhoos","Deccan United CC","Unstoppable"));
    static Set<String> divBGroupB = new HashSet<>(Arrays.asList("Baazigar of Bay","Chargers","Champions","Daredevils","Fire Friends","Titans","NextGen Tailenders","Ghadeer CC"));
    static Set<String> divCGroupA = new HashSet<>(Arrays.asList("Singh Warriors","Bay Boys","Stanford","Centurions","IndianChamps","YAM CC-14","Bleed Blue","Barely XI","Nexus XI","Super Saiyans"));
    static Set<String> divCGroupB = new HashSet<>(Arrays.asList("Alviso Warriors","Avengers","Bay Tigers","CricKings","MINIONS","Raynor Royals","Sunny","Super Giants","Vikings","WCC Gladiators"));
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
        try (BufferedReader br = new BufferedReader(new FileReader("/Users/sreevik/Desktop/NACL/Summer_2024_RR.csv"))) {
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
