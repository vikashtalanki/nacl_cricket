import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NACLUmpiringCheck {
    static Set<String> divAGroupA = new HashSet<>(Arrays.asList("Astros+","Mighty Warriors","Thunderbolts","JerseyXI","Singh Cricket Club","Ghadeer CC","Fire Friends","Lords","2point0","Knight Walkers (CA)","Unstoppable"));
    static Set<String> divAGroupB = new HashSet<>(Arrays.asList("Money Heist","Dark Knights","Seamers","Grizlies","Rising Stars","Smoking Sadhoos","Dragons","CSK Yorkers","P@nthers XI","Alviso Warriors","Baazigar of Bay"));
    static Set<String> divBGroupA = new HashSet<>(Arrays.asList("Breaking Bats","Royal Kings","MCC","Tailenders","Chargers","Titans","CricKings","Extraas","Avengers ","Kings X1","Veer Jawan"));
    static Set<String> divBGroupB = new HashSet<>(Arrays.asList("Mavericks","MINIONS","Champions","Gully Cricketers","Bleed Blue","Daredevils","YAM CC-14","Thunder Wolves XI","Raynor Royals","Royal Knights","Skippers"));
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
        try (BufferedReader br = new BufferedReader(new FileReader("/Users/sreevik/Desktop/NACL/Summer_2023_RR.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fixtures = line.split("\\t");
                if(map.get(fixtures[0]) == map.get(fixtures[3]) || map.get(fixtures[0]) == map.get(fixtures[4]) || map.get(fixtures[1]) == map.get(fixtures[3]) || map.get(fixtures[1]) == map.get(fixtures[4])) {
                    System.out.println(line);
                }
            }
            System.out.println("All Good");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
