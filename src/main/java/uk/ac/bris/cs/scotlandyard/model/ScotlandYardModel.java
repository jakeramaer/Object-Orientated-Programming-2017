package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

import java.util.*;
import java.util.function.Consumer;

import com.sun.javafx.collections.UnmodifiableListSet;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import java.util.stream.Collectors;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

	 //	Key attributes - Added in order of implementation.
	 public List<Boolean> rounds;
	 public Graph<Integer, Transport> graph;
	 private ArrayList<ScotlandYardPlayer> playerList = new ArrayList<>();
	 private Colour currentPlayer;
	 private Integer currentRound;
	 private Integer mrXLastLocation;
     private final List<Spectator> spectators = new ArrayList<>();
     private Set<Colour> winningPlayers = new HashSet<>();
	 //   ----------------------------------------------------------------

	 public ScotlandYardModel(List<Boolean> rounds,
							Graph<Integer, Transport> graph,
													PlayerConfiguration mrX,
													PlayerConfiguration firstDetective,
													PlayerConfiguration... restOfTheDetectives) {
				//	Initial conditions
                this.currentRound = 0;
				this.currentPlayer = BLACK;
				this.mrXLastLocation = 0;

				//  Graph & Round Conditions
				this.rounds = requireNonNull(rounds);
				this.graph = requireNonNull(graph);
				this.rounds = rounds;
				this.graph = graph;
				if (rounds.isEmpty()) { throw new IllegalArgumentException("Empty rounds"); }
				if (graph.isEmpty())  { throw new IllegalArgumentException("Empty graph"); }

				//	Check mrX is Black
				if (mrX.colour != BLACK) { throw new IllegalArgumentException("MrX should be Black");}

				// To test all configurations
				ArrayList<PlayerConfiguration> configurations = new ArrayList<>();

				    // Testing rest of detectives are not null then adds ALL PLAYERS to configurations
					for (PlayerConfiguration configuration : restOfTheDetectives) {
						configurations.add(requireNonNull(configuration));
					}
					configurations.add(0, firstDetective);
					configurations.add(0, mrX);

					//	Tests locations and Colours aren't duplicated
					Set<Integer> locationSet = new HashSet<>();
					for (PlayerConfiguration configuration : configurations) {
						if (locationSet.contains(configuration.location)) { throw new IllegalArgumentException("Duplicate location"); }
						locationSet.add(configuration.location);
					}
					Set<Colour> colourSet = new HashSet<>();
					for (PlayerConfiguration configuration : configurations) {
						if (colourSet.contains(configuration.colour)) { throw new IllegalArgumentException("Duplicate colour"); }
						colourSet.add(configuration.colour);
					}

					for (PlayerConfiguration configuration : configurations) {
						//	Test no tickets are Null
						if(configuration.tickets.get(Ticket.TAXI)             == null ||
								configuration.tickets.get(Ticket.BUS)         == null ||
							    configuration.tickets.get(Ticket.UNDERGROUND) == null ||
							    configuration.tickets.get(Ticket.DOUBLE)      == null ||
							    configuration.tickets.get(Ticket.SECRET)      == null)
								throw new IllegalArgumentException("Null Tickets");
						//	Test detectives don't have Double or Secret Tickets
						if (configuration.colour.isDetective())	{
								if(configuration.tickets.get(SECRET) != 0)
									throw new IllegalArgumentException("Detectives cannot have double tickets");
								if(configuration.tickets.get(DOUBLE) != 0)
									throw new IllegalArgumentException("Detectives cannot have double tickets");
						}
						//  Creates new ScotlandYardPlayer and adds them to playerList for each configuration
						ScotlandYardPlayer temp = new ScotlandYardPlayer(configuration.player, configuration.colour, configuration.location, configuration.tickets);
						playerList.add(temp);
					}
}

     //  --------------------------------------------------------------------------------------------------------------------
     //  SMALL HELPER METHODS

	 public ScotlandYardPlayer getPlayerFromColour(Colour colour) {
		 for(ScotlandYardPlayer player : playerList)	{
			if(player.colour() == colour)	{ return player; }
		}
		return null;   // We know they're not null because we checked in the constructor, this is just to make the function happy.
	 }

	 public void newRound()    {
         currentRound++;
         updateMrX();
         if(isGameOver())   {
             notifyGameOver();
             return;
         }
         for(Spectator spectator : spectators)   { spectator.onRoundStarted(this, currentRound); }
     }

     private void nextPlayer() {
	    ScotlandYardPlayer playerTemp = getPlayerFromColour(getCurrentPlayer());
	    int i = 0;
	    for (ScotlandYardPlayer player : playerList)    {
	        if(i == playerList.size()-1)  { 	     //  If last player, current player becomes BLACK and round is complete.
				currentPlayer = BLACK;
                break;
	        }
	        else if(playerTemp.equals(player))   {
	            currentPlayer = playerList.get(i+1).colour();
	            break;
	        }
	        i++;
        }
     }

     private Colour previousPlayer() {		// 	This helps with the odd way the player updates before the move is made
         int i = 0;
         if (currentPlayer == BLACK) { return null; }	// Because no previous player in that round
             for (ScotlandYardPlayer player : playerList) {
                 if (player.colour() == currentPlayer) {
                     return playerList.get(i - 1).colour();
                 }
                 i++;
             }
             return null;
     }

	 //	used for updating mrXLastLocation
	 private void updateMrX() {
		 if(currentRound !=0) {
			 if (getRounds().get(currentRound - 1)) {
				 mrXLastLocation = getPlayerFromColour(BLACK).location();
		 	 }
		 }
	 }

	 // Requests moves from colour
	 private void requestMove(Colour colour) {
		 if(isGameOver() && currentRound == 0)	{ throw new IllegalStateException("Game over before begun"); } // for GameOverTest
		 ScotlandYardPlayer player = getPlayerFromColour(colour);
		 player.player().makeMove(this, player.location(), possibleMoves(colour), this);
	 }

	 private void notifyGameOver() {
		 for (Spectator spectator : spectators) { spectator.onGameOver(this, getWinningPlayers()); }
	 }

	 private void notifyMoveMade(Move move)  {
         for (Spectator spectator : spectators) { spectator.onMoveMade(this, move); }
     }


	 //  Functions to fill up winningPlayers
	 private void detectivesWin()    {
		for (ScotlandYardPlayer player : playerList)    {
			if (player.colour() != BLACK) {
				winningPlayers.add(player.colour());
			}
		}
	 }
	 private void mrXWins()   { winningPlayers.add(BLACK); }

     //  --------------------------------------------------------------------------------------------------------------------
     //  LARGE METHODS

	 // Implementing Consumer<Move>. Acts as move validity test. BIG FUNCTION
	 @Override
     public void accept(Move t) {
		 requireNonNull(t);
		 if(!(possibleMoves(currentPlayer).contains(t)))   {throw new IllegalArgumentException("Illegal Move");}
		 // Visitor
         t.visit(this);
		 // GameOver is only called here and in the newRound method.
         if(isGameOver()) {
             notifyGameOver();
             return;
         }
		 // If currentPlayer is BLACK then rotation is complete and accept method stops cycling
         if(currentPlayer == BLACK)    {
             for(Spectator spectator : spectators)   { spectator.onRotationComplete(this); }
         }
         // This is where the method 'links' to next player
		 if(currentPlayer != BLACK && !isGameOver()) {
             requestMove(currentPlayer);
         }
     }

     //	possibleMoves SecretMoves helper function
     private void addSecretMoves(Set<Move> moveSet, Collection<Edge<Integer, Transport>> edges, Colour colour)	{
		 ScotlandYardPlayer playerTemp = getPlayerFromColour(colour);
		 for(Edge<Integer, Transport> edge : edges) {
		 	 // Creating a new TicketMove with SECRET transport for every edge away from mrX
			 TicketMove temp = new TicketMove(colour, SECRET, edge.destination().value());
			 Boolean validMove = true;
			 if(playerTemp.tickets().get(SECRET) <= 0)  { validMove = false; }
			 for (ScotlandYardPlayer player : playerList) {
				 if (edge.destination().value() == player.location() && player.colour() != BLACK)
				 { validMove = false; }
			 }
			 if (validMove) { moveSet.add(temp); }
		 }
	 }

	 //	possibleMoves DoubleMoves helper function which generates the second moveSet from each individual move from first moveSet
	 private Set<Move> makeSecondMoveSet(Colour colour, Move move1)	{
		 // Values
		 ScotlandYardPlayer player = getPlayerFromColour(colour);
		 Set<Move> moveSet2 = new HashSet<>();
		 if(move1 instanceof TicketMove) {
			 Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode(((TicketMove)move1).destination())); // Down-casting necessary to access .destination()
		 	 //	checks if they're valid
		 	 for(Edge<Integer, Transport> edge : edges) {
		 		 //	creates a new ticketMove
				 TicketMove temp2 = new TicketMove(colour, fromTransport(edge.data()), edge.destination().value());
				 Boolean validMove = true;
				 if(player.tickets().get(fromTransport(edge.data())) == 0)  { validMove = false; }
				 //	This is if the player doesn't have enough tickets
				 if(fromTransport(edge.data()) == ((TicketMove) move1).ticket() && player.tickets().get(fromTransport(edge.data())) == 1)  { validMove = false; }
				 for (ScotlandYardPlayer player2 : playerList) {
					 if (edge.destination().value() == player2.location() && player2.colour() != BLACK) { validMove = false; }
				 }
				 //	stores secondary moves in moveSet2
				 if (validMove) { moveSet2.add(temp2); }
		 	 }
		 	 // Adding SecretMoves
			 addSecretMoves(moveSet2, edges, currentPlayer);
		 }
		 return moveSet2;
	 }

     // Generates a list of possible moves
     private Set<Move> possibleMoves(Colour colour)  {
         ScotlandYardPlayer player = getPlayerFromColour(colour);
         // Creating new moveSet and collection of edges
         Set<Move> moveSet = new HashSet<>();
         Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode(player.location()));

         // Adding TicketMoves
         for(Edge<Integer, Transport> edge : edges) {
         	 TicketMove temp = new TicketMove(colour, fromTransport(edge.data()), edge.destination().value());
         	 Boolean validMove = true;
         	 if(player.tickets().get(fromTransport(edge.data())) == 0)                          { validMove = false; }
         	 for (ScotlandYardPlayer player1 : playerList) {
         		 if (edge.destination().value() == player1.location() && player1.colour() != BLACK) { validMove = false; }
         	 }
         	if (validMove) { moveSet.add(temp); }
         }
         // Adding SecretMoves
		 addSecretMoves(moveSet, edges, colour);

         //	Adding DoubleMoves
		 Set<Move> doubleMoveSet = new HashSet<>();
		 if(getPlayerFromColour(colour).tickets().get(DOUBLE) != 0) {
		 	for(Move move1 : moveSet) {
		 		TicketMove temp1 = (TicketMove)move1;
				Set<Move> moveSet2 = makeSecondMoveSet(colour, move1);
		 		for(Move move2 : moveSet2)	{
					TicketMove temp2 = (TicketMove)move2;
		 			DoubleMove temp = new DoubleMove(colour, temp1, temp2);
		 			doubleMoveSet.add(temp);
				}
			}
			if(getCurrentRound() < getRounds().size()-1){		//  No double moves if final round
				moveSet.addAll(doubleMoveSet);
		 	}
		 }
         if(moveSet.size() <= 0 && colour != BLACK) { moveSet.add(new PassMove(colour)); }	//	Adding PassMoves for detectives with no tickets
		 return moveSet;
     }

    // --------------------------------------------------------------------------------------------------------------------
    // Visitor Overriding

    @Override
    public void visit(PassMove move) {
        nextPlayer();
        for(Spectator spectator : spectators)   { spectator.onMoveMade(this, move); }
    }

    @Override
    public void visit(TicketMove move) {
        TicketMove tempHidden = new TicketMove(BLACK, move.ticket(), mrXLastLocation);

        // Removing ticket after use (giving it to MrX)
        getPlayerFromColour(currentPlayer).location(move.destination());
        getPlayerFromColour(currentPlayer).removeTicket(move.ticket());
        if(currentPlayer!=BLACK)   { getPlayerFromColour(BLACK).addTicket(move.ticket()); }
        nextPlayer();
        if(currentPlayer == getPlayers().get(1)) { newRound(); }	//	i.e if player is next after MrX

        // Updating Spectators - This is long because of BLACK hidden moves, look at the move returned to spectators.
        if(previousPlayer() != BLACK)                                      { notifyMoveMade(move); }
        if(previousPlayer()== BLACK && currentRound == 0)                  { notifyMoveMade(tempHidden); }
        if(previousPlayer()== BLACK && getRounds().get(currentRound-1))    { notifyMoveMade(move); }
        if(previousPlayer()== BLACK && !(getRounds().get(currentRound-1))) { notifyMoveMade(tempHidden); }
    }

    @Override
    public void visit(DoubleMove move) {
        TicketMove temp1 = new TicketMove(BLACK, move.firstMove().ticket(), mrXLastLocation);
        TicketMove temp2 = new TicketMove(BLACK, move.secondMove().ticket(), mrXLastLocation);
        getPlayerFromColour(BLACK).removeTicket(DOUBLE);
        nextPlayer();
        //	If the current or next rounds are reveal rounds, change temp moves (which are hidden) to the actual moves
        if(getRounds().get(currentRound))  { temp1 = move.firstMove(); }
        if(getRounds().get(currentRound + 1))   { temp2 = move.secondMove(); }
        if(getRounds().get(currentRound) && !(getRounds().get(currentRound + 1))) {
            //	This one is a bit weird, it tells the spectators the destination of the first move, but not the second
            temp2 = new TicketMove(BLACK, move.secondMove().ticket(), move.firstMove().destination());
        }
        //	Updating spectators, locations, ticketCounts etc IN THE CORRECT ORDER
        DoubleMove finalDouble = new DoubleMove(BLACK, temp1, temp2);
        notifyMoveMade(finalDouble);

        getPlayerFromColour(BLACK).location(move.firstMove().destination());
        getPlayerFromColour(BLACK).removeTicket(move.firstMove().ticket());
        newRound();
        notifyMoveMade(temp1);

        getPlayerFromColour(BLACK).location(move.secondMove().destination());
        getPlayerFromColour(BLACK).removeTicket(move.secondMove().ticket());
        newRound();
        notifyMoveMade(temp2);
    }

    // --------------------------------------------------------------------------------------------------------------------
    //  HELPER GETTERS AND SETTERS FOR VARIABLES

    private ArrayList<ScotlandYardPlayer> getPlayerList() { return playerList; }

    private Integer getMrXLastLocation() { return mrXLastLocation; }


    //  -------------------------------------------------------------------------------------------------------------------
    //  TILO FUNCTIONS

	@Override
	public void registerSpectator(Spectator spectator) {
	    for(Spectator spec : spectators)    {
	        if(spectator.equals(spec))  { throw new IllegalArgumentException("spectator not in list"); }
        }
        requireNonNull(spectator);
		spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
        requireNonNull(spectator);
	    if(!getSpectators().contains(spectator))   { throw new IllegalArgumentException("spectator not in list"); }
		else spectators.remove(spectator);
	}

	@Override
	public void startRotate() {
			requestMove(currentPlayer);
    }

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		ArrayList<Colour> players = new ArrayList<>();
		for(ScotlandYardPlayer player : getPlayerList())	{ players.add(player.colour()); }
		return Collections.unmodifiableList(players);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
	    Set<Colour> a = new HashSet<>();    // Return empty list if game isn't over
	    if(isGameOver()) {
            return Collections.unmodifiableSet(winningPlayers);
        }
        return Collections.unmodifiableSet(a);
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
	    if(getPlayerFromColour(colour) == null) { return Optional.empty(); }
        if (colour == BLACK)    { return Optional.ofNullable(getMrXLastLocation()); }
        else {
            int a = getPlayerFromColour(colour).location();
            return Optional.ofNullable(a);
        }
    }

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
        if(getPlayerFromColour(colour) == null) { return Optional.empty(); }
        return Optional.ofNullable(getPlayerFromColour(colour).tickets().get(ticket));
	}

	@Override
	public boolean isGameOver() {
		int ticketCount = 0;
		for(ScotlandYardPlayer player :playerList)	{
			//	If detective finds mrX
			if(player.colour() != BLACK && player.location() == getPlayerFromColour(BLACK).location())	{
			    detectivesWin();
			    return true;
			}
			//  Checking ticket count
			if(player.colour() != BLACK)	{
				for(Ticket key : player.tickets().keySet())	{
					ticketCount += player.tickets().get(key);
				}
			}
		}
		//	If all detectives have 0 tickets
		if(ticketCount == 0)	{
		    mrXWins();
			return true;
		}
		//	If mrX has no moves left
		if(possibleMoves(BLACK).size() == 0 && currentPlayer == BLACK)	{
		    detectivesWin();
		    return true;
		}
		//	If all rounds used up
		if(getCurrentRound() == getRounds().size() && currentPlayer == BLACK) {
		    mrXWins();
		    return true;
        }
        return false;
	}

	@Override
	public Colour getCurrentPlayer() { return currentPlayer; }

	@Override
	public int getCurrentRound() { return currentRound; }

	@Override
    public List<Boolean> getRounds() { return Collections.unmodifiableList(rounds); }

	@Override
	public Graph<Integer, Transport> getGraph() { return new ImmutableGraph<>(graph); }
}
