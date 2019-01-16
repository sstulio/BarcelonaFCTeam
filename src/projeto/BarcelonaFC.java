package projeto;

import simple_soccer_lib.AbstractTeam;
import simple_soccer_lib.PlayerCommander;


public class BarcelonaFC extends AbstractTeam {

	public BarcelonaFC(String suffix) {
		super("BarcelonaFC_" + suffix, 7, true);
	}

	@Override
	protected void launchPlayer(int ag, PlayerCommander commander) {
		double targetX, targetY;
		
		if (ag == 0) {
			targetY = 34.0d / 2; //posição que aparece mais baixa no monitor
		} else {
			targetY = -34.0d / 2;  //posição mais alta
		}
		
		targetX = 52.5d / 2;
		
		BarcelonaPlayer pl = new BarcelonaPlayer(commander, targetX, targetY);
		pl.start();
	}

}
