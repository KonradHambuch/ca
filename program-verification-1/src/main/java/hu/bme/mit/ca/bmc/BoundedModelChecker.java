package hu.bme.mit.ca.bmc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.CFA.Edge;
import hu.bme.mit.theta.cfa.CFA.Loc;
import hu.bme.mit.theta.core.stmt.Stmt;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;

public final class BoundedModelChecker implements SafetyChecker {

	private final CFA cfa;
	private final int bound;
	private final int timeout;
	private boolean wasAlreadyUnsafe;

	private BoundedModelChecker(final CFA cfa, final int bound, final int timeout) {
		checkArgument(bound >= 0);
		checkArgument(timeout >= 0);

		this.cfa = checkNotNull(cfa);
		this.bound = bound;
		this.timeout = timeout;
	}

	public static BoundedModelChecker create(final CFA cfa, final int bound, final int timeout) {
		return new BoundedModelChecker(cfa, bound, timeout);
	}

	@Override
	public SafetyResult check() {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		while (stopwatch.elapsed(TimeUnit.SECONDS) < timeout) {			
			ArrayList<Stmt> stmts = new ArrayList<Stmt>();		
			BFS(cfa.getInitLoc(), 0, stmts);
			if(wasAlreadyUnsafe) {
				return SafetyResult.UNSAFE;
			}
			else {
				return SafetyResult.UNKNOWN;
			}
		}

		stopwatch.stop();

		return SafetyResult.TIMEOUT;
	}
	public void BFS(Loc startLoc, int depth, ArrayList<Stmt> stmts) {	
		System.out.println("Actual node: " + startLoc.getName() +" DEPTH: "+ depth + " Statmens: " + stmts);
		if(depth >= bound) {
			System.out.println("DEPTH>=BOUND");
			return;
		}
		if(startLoc == cfa.getErrorLoc().get()) {
			System.out.println("ERRORLOC-----------------------------------------------");
			if(checkWithSatSolver(stmts)) {	
				System.out.println("CHECK FAILED");
				wasAlreadyUnsafe = true;
			}			
		}
		Collection<Edge> initEdges = startLoc.getOutEdges();
		for(Edge edge: initEdges) {		
			stmts.add(edge.getStmt());
			BFS(edge.getTarget(), depth+1, stmts);
		}
		System.out.println("END");
	}
	public boolean checkWithSatSolver(List<Stmt> stmts) {			
		System.out.println(stmts);
		
		final Collection<Expr<BoolType>> exprs = StmtToExprTransformer.unfold(stmts);

		System.out.println(exprs);

		final Solver solver = Z3SolverFactory.getInstance().createSolver();

		solver.add(exprs);
		solver.check();
		if(solver.getStatus().isSat()) {
			System.out.println("TRUE");
		}
		else {
			System.out.println("FALSE");
		}
		return solver.getStatus().isSat();
	}
}
