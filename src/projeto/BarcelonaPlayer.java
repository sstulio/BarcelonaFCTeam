package projeto;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;

import simple_soccer_lib.PlayerCommander;
import simple_soccer_lib.perception.FieldPerception;
import simple_soccer_lib.perception.MatchPerception;
import simple_soccer_lib.perception.PlayerPerception;
import simple_soccer_lib.utils.EFieldSide;
import simple_soccer_lib.utils.EMatchState;
import simple_soccer_lib.utils.Vector2D;

public class BarcelonaPlayer extends Thread {
	private static final double ERROR_RADIUS = 2.0d;
	private static final double KICK_RADIUS = 0.6d;
	private static final double KICK_FACTOR = 4.5d;
	
	private static enum EstrategiaTime {ATAQUE, DEFESA};
	
	private enum State {
		ATTACKING, RETURN_TO_HOME, FOLLOW, PASSING_BALL
	};

	private PlayerCommander commander;
	private State state;

	private PlayerPerception selfPerception;
	private FieldPerception fieldPerception;
	private MatchPerception matchPerception;
	 
	private Vector2D playerDeafaultPosition;

	public BarcelonaPlayer(PlayerCommander player, double x, double y) {
		commander = player;
		playerDeafaultPosition = new Vector2D(x, y);
	}

	@Override
	public void run() {
		_printf("Waiting initial perceptions...");
		selfPerception = commander.perceiveSelfBlocking();
		fieldPerception = commander.perceiveFieldBlocking();
		matchPerception = commander.perceiveMatchBlocking();
		
		state = State.RETURN_TO_HOME;		
		
		switch (selfPerception.getUniformNumber()) {
		case 1:
			stegen();
			break;
		case 2:
			pique();
			break;
		case 3:
			coutinho();
			break;
		case 4:
			busquets();
			break;
		case 5:
			suarez();
			break;
		case 6:
		case 7:
			messi();
		default:
			break;
		}
	}

	
	private void updatePerceptions() {
		PlayerPerception newSelf = commander.perceiveSelf();
		FieldPerception newField = commander.perceiveField();
		MatchPerception newMatch = commander.perceiveMatch();

		if (newSelf != null) {
			this.selfPerception = newSelf;
		}
		if (newField != null) {
			this.fieldPerception = newField;
		}
		if (newMatch != null) {
			this.matchPerception = newMatch;
		}
	}
	
	private PlayerPerception getCloserPlayer() {

		Vector2D selfPosition = selfPerception.getPosition();
		ArrayList<PlayerPerception> teamPlayers = fieldPerception.getTeamPlayers(selfPerception.getSide());

		teamPlayers.remove(selfPerception);

		int closerPlayerIndex = -1;
		double closerDistance = Double.MAX_VALUE;
		for (int i = 0; i < teamPlayers.size(); i++) {
			double d = selfPosition.distanceTo(teamPlayers.get(i).getPosition());
			if (d < closerDistance) {
				closerPlayerIndex = i;
				closerDistance = d;
			}
		}

		PlayerPerception j = teamPlayers.get(closerPlayerIndex);
		return j;
	}

	private boolean isCloserToBall() {

		Vector2D ballPosition = fieldPerception.getBall().getPosition();
		Vector2D selfPosition = selfPerception.getPosition();
		
		ArrayList<PlayerPerception> teamPlayers = fieldPerception.getTeamPlayers(selfPerception.getSide());
		
		double minDistance = teamPlayers.stream()
				.map(p -> p.getPosition().distanceTo(ballPosition))
				.min(Comparator.comparing(Double::valueOf))
				.get();

		return selfPosition.distanceTo(ballPosition) == minDistance;
	}

	private boolean isCloseTo(Vector2D position) {
		Vector2D myPos = selfPerception.getPosition();
		return Vector2D.distance(myPos, position) <= ERROR_RADIUS;
	}

	private boolean arrivedAtBall() {
		Vector2D myPos = selfPerception.getPosition();
		return Vector2D.distance(myPos, fieldPerception.getBall().getPosition()) <= KICK_RADIUS;
	}

	private void turnTo(Vector2D position) {
		Vector2D myPos = selfPerception.getPosition();

		Vector2D newDirection = position.sub(myPos);
		commander.doTurnToDirectionBlocking(newDirection);
	}

	private boolean isAlignedTo(Vector2D position) {
		Vector2D myPos = selfPerception.getPosition();
		if (position == null || myPos == null) {
			return false;
		}
		double angle = selfPerception.getDirection().angleFrom(position.sub(myPos));
		return angle < 15.0d && angle > -15.0d;
	}

	/*If close to ball and state is RETURN_BASE go to ATTAKCKING stage*/
	private void stateReturnToHomeBase() {
		if (isCloserToBall()) {
			state = State.ATTACKING;
			return;
		}

		if (!isCloseTo(playerDeafaultPosition)) {
			if (isAlignedTo(playerDeafaultPosition)) {
				commander.doDashBlocking(50.0d);
			} else {
				turnTo(playerDeafaultPosition);
			}
		}
	}

	
	private void stateAttacking() {
		if (!isCloserToBall()) {
			
			//Prevent zagueiros from leaving position and run towards the ball
			
			if(selfPerception.getUniformNumber() != 2) {
				state = State.FOLLOW;
				return;
			}else {
				state = State.RETURN_TO_HOME;
				return;
			}			
		}

		Vector2D golPosition;
		Vector2D ballPosition = fieldPerception.getBall().getPosition();

		if (arrivedAtBall()) {
			golPosition = new Vector2D(50 * selfPerception.getSide().value(), 0);
			// Vector2D novaPosicao = posicaoChutarGol();
			if (selfPerception.getSide() == EFieldSide.LEFT) {

				if (selfPerception.getPosition().getX() > 30.0d) {
					if(isAlignedTo(golPosition)){
						commander.doKick(100.0d, 0d);					
					}else {
						turnTo(golPosition);
						commander.doKickBlocking(100.0d, 0d);
					}
					
					
				} else if (selfPerception.getPosition().getX() <=  30.0d) {
					PlayerPerception j = getCloserPlayer();
					if(selfPerception.getPosition().getX() < j.getPosition().getX()) { // verify if closer player is in front
						turnTo(golPosition);
						tick();
						commander.doKick(45.0d, 0d);
					}else { // toca bola
						double mag = j.getPosition().sub(selfPerception.getPosition()).magnitude();
						Vector2D frontPosition = new Vector2D(j.getPosition().getX() + 15.0, j.getPosition().getY());
						commander.doTurnToPointBlocking(frontPosition);					
						commander.doKickBlocking(mag*KICK_FACTOR, 0d);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}							
					
				}
			} else {
				if (selfPerception.getPosition().getX() < -30.0d) {
					turnTo(golPosition);
					tick();
					commander.doKick(100.0d, 0d); // kick toward adversary goal
				} else if (selfPerception.getPosition().getX() >= -30.0d) {
					PlayerPerception j = getCloserPlayer();
					if(selfPerception.getPosition().getX() > j.getPosition().getX()) { // verify if closer plaer is in front
						turnTo(golPosition);
						tick();
						commander.doKick(45.0d, 0d);
					}else { // toca bola
						double mag = j.getPosition().sub(selfPerception.getPosition()).magnitude();
						commander.doTurnToPointBlocking(j.getPosition());					
						commander.doKickBlocking(mag*KICK_FACTOR, 0d);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}	
				}
			}

		} else {
			if (isAlignedTo(ballPosition) && selfPerception.getPosition().getX() < fieldPerception.getBall().getPosition().getX()) {
				_printf("ATK: Running to the ball...");
				commander.doDashBlocking(100.0d);
			} else {
				_printf("ATK: Turning...");
				turnTo(ballPosition);
			}
		}
	}
	
	private void stateFollowBall() {
		if (isCloserToBall()) {
			state = State.ATTACKING;
			return;
		}
		double y = 0;
		if(selfPerception.getUniformNumber() == 6) {
			y = -8*selfPerception.getSide().value();
		}else if(selfPerception.getUniformNumber() == 5) {
			y = 8*selfPerception.getSide().value();
		}else if(selfPerception.getUniformNumber() == 4) {
			y = -20*selfPerception.getSide().value();
		}else if(selfPerception.getUniformNumber() == 3) {
			y = 20*selfPerception.getSide().value();
		}
		
		Vector2D golPosition = new Vector2D(50 * selfPerception.getSide().value(), 0);
		Vector2D myPosition = selfPerception.getPosition();
		Vector2D ballPosition = fieldPerception.getBall().getPosition();
		if (isAlignedTo(new Vector2D(ballPosition.getX(), y))) {
			_printf("ATK: Running to the ball...");
			commander.doDashBlocking(100.0d);
		} else {
			_printf("ATK: Turning...");
			commander.doTurnToPointBlocking(new Vector2D(ballPosition.getX(), y));
		}		
	}
	
	private void statePassingBall() {
//		if (!isCloserToBall()) {
//			state = State.RETURN_TO_HOME;
//			return;
//		}

		Vector2D golPosition;
		Vector2D ballPosition = fieldPerception.getBall().getPosition();

		if (arrivedAtBall()) {
			PlayerPerception p = getCloserPlayer();
			double mag = p.getPosition().sub(selfPerception.getPosition()).magnitude();
			turnTo(p.getPosition());			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			commander.doKickToPointBlocking(mag * KICK_FACTOR, p.getPosition());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			state = State.FOLLOW;
		} else {
			if (isAlignedTo(ballPosition)) {
				_printf("ATK: Running to the ball...");
				commander.doDashBlocking(70.0d);
			} else {
				_printf("ATK: Turning...");
				turnTo(ballPosition);
			}
			//dash(ballPosition);
		}
	}

	private void tick() {
		try {
			Thread.sleep(100); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void dash(Vector2D point) {
		if (selfPerception.getPosition().distanceTo(point) <= ERROR_RADIUS)
			return;
		if (!isAlignedTo(point))
			commander.doTurnToPointBlocking(point);
		commander.doDashBlocking(90);
	}
	private void dashBall(Vector2D point) {
		if (selfPerception.getPosition().distanceTo(point) <= KICK_RADIUS)
			return;
		if (!isAlignedTo(point))
			commander.doTurnToPointBlocking(point);
		commander.doDashBlocking(80);
	}
	// for debugging
	public void _printf(String format, Object... objects) {
		String teamPlayer = "";
		if (selfPerception != null) {
			teamPlayer += "[" + selfPerception.getTeam() + "/" + selfPerception.getUniformNumber() + "] ";
		}
		//System.out.printf(teamPlayer + format + "%n", objects);
	}

	private void executeStateMachine() {
		if(selfPerception.getUniformNumber() == 3) {
			System.out.println("Estado do Jogador 3 :"+ state);
		}
		switch (state) {
		
		case ATTACKING:			
			stateAttacking();
			break;
		case RETURN_TO_HOME:
			stateReturnToHomeBase();
			break;
		case PASSING_BALL:
			statePassingBall();
			break;
		case FOLLOW:
			stateFollowBall();
			break;
		default:
			_printf("Invalid state: %s", state);
			break;
		}
	}
	
	private void messi() {
		try {
			Thread.sleep(5000); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double xInit = -10; double yInit = -8;
		playerDeafaultPosition = new Vector2D(xInit, yInit);
		if (selfPerception.getSide() == EFieldSide.RIGHT) { // ajusta a posição base de acordo com o lado do jogador
														// (basta mudar o sinal do x)
			playerDeafaultPosition.setX(-playerDeafaultPosition.getX());
		}
		while (commander.isActive()) {
			updatePerceptions(); // deixar aqui, no começo do loop, para ler o resultado do 'move'
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF:
				commander.doMoveBlocking(xInit, yInit);				
				break;
			case PLAY_ON:
				executeStateMachine();
				break;
			case KICK_OFF_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_OFF_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_LEFT: // escanteio time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_RIGHT: // escanteio time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_IN_LEFT: // lateral time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
			case KICK_IN_RIGHT: // lateal time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_LEFT: // Tiro livre time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_RIGHT: // Tiro Livre time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.RETURN_TO_HOME;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.RETURN_TO_HOME;
					executeStateMachine();
				}
				break;
			case AFTER_GOAL_LEFT:				
				commander.doMoveBlocking(xInit, yInit);
				break;
			case AFTER_GOAL_RIGHT:				
				commander.doMoveBlocking(xInit, yInit);
				break;
			case INDIRECT_FREE_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case INDIRECT_FREE_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			default:
				break;
			}

		}
	}

	private void coutinho() {
		try {
			Thread.sleep(5000); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		int xInit = -25;
		int yInit = 10;
		playerDeafaultPosition = new Vector2D(xInit*selfPerception.getSide().value(), yInit*selfPerception.getSide().value());
		
		while (commander.isActive()) {
			updatePerceptions(); // deixar aqui, no começo do loop, para ler o resultado do 'move'
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF:
				commander.doMoveBlocking(xInit, yInit);				
				break;
			case PLAY_ON:
				state = State.ATTACKING;
				executeStateMachine();
				break;
			case KICK_OFF_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_OFF_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_LEFT: // escanteio time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_RIGHT: // escanteio time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_IN_LEFT: // lateral time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
			case KICK_IN_RIGHT: // lateal time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_LEFT: // Tiro livre time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_RIGHT: // Tiro Livre time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					executeStateMachine();
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					executeStateMachine();
				}
				break;
			case AFTER_GOAL_LEFT:
				commander.doMoveBlocking(xInit, yInit);		
				break;
			case AFTER_GOAL_RIGHT:
				commander.doMoveBlocking(xInit, yInit);	
				break;
			case INDIRECT_FREE_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case INDIRECT_FREE_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			default:
				break;
			}
		}
	}

	private void stegen() {
		double xInit = -48, yInit = 0, ballX = 0, ballY = 0;
		EFieldSide side = selfPerception.getSide();
		Vector2D initPos = new Vector2D(xInit * side.value(), yInit * side.value());
		Vector2D ballPos;
		playerDeafaultPosition = new Vector2D(xInit, yInit);
		Rectangle area = side == EFieldSide.LEFT ? new Rectangle(-62, -30, 26, 50) : new Rectangle(46, -30, 26, 50);
		while (true) {
			updatePerceptions();
			ballPos = fieldPerception.getBall().getPosition();
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF: // posiciona
				commander.doMoveBlocking(xInit, yInit);								
				break;
			case PLAY_ON:
				ballX = fieldPerception.getBall().getPosition().getX() - (EFieldSide.LEFT.value() * 10);
				ballY = fieldPerception.getBall().getPosition().getY();
				if (arrivedAtBall()) { // chutar
					//commander.doKickBlocking(100.0d, 0.0d);
					turnTo(new Vector2D(0, 0));
					commander.doCatchBlocking(0);
					commander.doKickBlocking(100d, 0);
				} else if (area.contains(ballX, ballY)) { // defender
					dashBall(ballPos);
				} else if (!isCloseTo(initPos)) { // recuar					
					dash(initPos);
				} else { // olhar para a bola
					turnTo(ballPos);					
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					if(arrivedAtBall()) {
						commander.doTurnToPointBlocking(new Vector2D(0, 0));
						commander.doKickBlocking(100, 0);
					}else {
						dashBall(ballPos);
					}
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					if(arrivedAtBall()) {
						commander.doTurnToPointBlocking(new Vector2D(0, 0));
						commander.doKickBlocking(100, 0);
					}else {
						dashBall(ballPos);
					}
				}
				break;
			default:
				break;
			}
		}
	}
	
	private void pique() {
		// TODO Auto-generated method stub
		try {
			Thread.sleep(5000); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int xInit = -38;
		int yInit = 0;
		EFieldSide side = selfPerception.getSide();
		Vector2D initPos = new Vector2D(xInit * side.value(), yInit * side.value());
		playerDeafaultPosition = initPos;
		Vector2D ballPos;
		double ballX = 0, ballY = 0;
		
		Rectangle area = side == EFieldSide.LEFT ? new Rectangle(-52, -25, 32, 50) : new Rectangle(25, -25, 32, 50);
		
		while (commander.isActive()) {
			updatePerceptions(); // deixar aqui, no começo do loop, para ler o resultado do 'move'
			ballPos = fieldPerception.getBall().getPosition();
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF:				
				commander.doMoveBlocking(-38, 0);				
				break;
			case PLAY_ON:
				ballX = fieldPerception.getBall().getPosition().getX();
				ballY = fieldPerception.getBall().getPosition().getY();
				if (arrivedAtBall()) { // chutar
					commander.doKickBlocking(100.0d, 0.0d);
				} else if (area.contains(ballX, ballY)) { // defender
					dashBall(ballPos);
				} else if (!isCloseTo(initPos)) { // recuar					
					dash(initPos);
				} else { // olhar para a bola
					turnTo(ballPos);					
				}
				break;
			case KICK_OFF_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_OFF_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_LEFT: // escanteio time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_RIGHT: // escanteio time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_IN_LEFT: // lateral time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
			case KICK_IN_RIGHT: // lateal time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_LEFT: // Tiro livre time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_RIGHT: // Tiro Livre time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					executeStateMachine();
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					executeStateMachine();
				}
				break;
			case AFTER_GOAL_LEFT:
				commander.doMoveBlocking(-38, 0);
				break;
			case AFTER_GOAL_RIGHT:
				commander.doMoveBlocking(-38, 0);
				break;			
			default:
				break;
			}
		}
	}
	
	private void busquets() {
		// TODO Auto-generated method stub
		try {
			Thread.sleep(5000); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int xInit = -25;
		int yInit = -10;
		playerDeafaultPosition = new Vector2D(xInit*selfPerception.getSide().value(), yInit *selfPerception.getSide().value());
		
		while (commander.isActive()) {
			updatePerceptions(); // deixar aqui, no começo do loop, para ler o resultado do 'move'
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF:
				commander.doMoveBlocking(xInit, yInit);				
				break;
			case PLAY_ON:
				state = State.ATTACKING;
				executeStateMachine();
				break;
			case KICK_OFF_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_OFF_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					//if(closer)
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_LEFT: // escanteio time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_RIGHT: // escanteio time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_IN_LEFT: // lateral time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
			case KICK_IN_RIGHT: // lateal time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_LEFT: // Tiro livre time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_RIGHT: // Tiro Livre time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					executeStateMachine();
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					executeStateMachine();
				}
				break;
			case AFTER_GOAL_LEFT:
				commander.doMoveBlocking(xInit, yInit);	
				break;
			case AFTER_GOAL_RIGHT:
				commander.doMoveBlocking(xInit, yInit);	
				break;
			case INDIRECT_FREE_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case INDIRECT_FREE_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			default:
				break;
			}
		}
	}

	private void suarez() {
		// TODO Auto-generated method stub
		try {
			Thread.sleep(5000); // espera, para dar tempo de ver as mensagens iniciais
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int xInit = -10;
		int yInit = 8;
		playerDeafaultPosition = new Vector2D(xInit*selfPerception.getSide().value(), yInit*selfPerception.getSide().value());
		while (commander.isActive()) {
			
			updatePerceptions(); // deixar aqui, no começo do loop, para ler o resultado do 'move'
			switch (matchPerception.getState()) {
			case BEFORE_KICK_OFF:
				commander.doMoveBlocking(xInit, yInit);								
				break;
			case PLAY_ON:
				//state = State.FOLLOW;
				executeStateMachine();
				break;
			case KICK_OFF_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_OFF_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_LEFT: // escanteio time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case CORNER_KICK_RIGHT: // escanteio time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case KICK_IN_LEFT: // lateral time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
			case KICK_IN_RIGHT: // lateal time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_LEFT: // Tiro livre time esquerdo
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_RIGHT: // Tiro Livre time direito
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case FREE_KICK_FAULT_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case GOAL_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					executeStateMachine();
				}
				break;
			case GOAL_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					executeStateMachine();
				}
				break;
			case AFTER_GOAL_LEFT:
				commander.doMoveBlocking(xInit, yInit);
				break;
			case AFTER_GOAL_RIGHT:
				commander.doMoveBlocking(xInit, yInit);
				break;
			case INDIRECT_FREE_KICK_LEFT:
				if (selfPerception.getSide() == EFieldSide.LEFT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			case INDIRECT_FREE_KICK_RIGHT:
				if (selfPerception.getSide() == EFieldSide.RIGHT) {
					state = State.PASSING_BALL;
					executeStateMachine();
				}
				break;
			default:
				break;
			}

		}
	}

}
