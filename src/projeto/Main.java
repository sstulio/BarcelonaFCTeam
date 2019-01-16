package projeto;

import java.net.UnknownHostException;

public class Main {

	public static void main(String[] args) throws UnknownHostException {
		BarcelonaFC team1 = new BarcelonaFC("a");
//		BarcelonaFC team2 = new BarcelonaFC("b");

		team1.launchTeamAndServer();
//		team2.launchTeam();
	}

}
