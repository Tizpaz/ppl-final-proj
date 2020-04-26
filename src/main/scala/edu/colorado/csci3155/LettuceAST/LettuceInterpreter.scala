package LettuceAST


case object LettuceInterpreter {
    def evalProgram(p: Program, breakN: Int): Value = p match {
        case TopLevel(e) => {
            val (v, st) = evalExpr(0, breakN, e, EmptyEnvironment.asInstanceOf[LettuceEnvironment], StoreInterface.emptyStore)
            v
        }
        case _ => throw new IllegalArgumentException(s"Error: not a 'Top Level' Program: p: $p\n")
    }

    def binOp[T](currN: Int, breakN: Int, e1: Expr, e2: Expr, env: LettuceEnvironment, st: LettuceStore)
                (g: Value => T)
                (f: (T,T) => Value): (Value, LettuceStore) = {
        val (v1, st1) = evalExpr(currN, breakN, e1, env, st)
        val (v2, st2) = evalExpr(currN+1, breakN, e2, env, st1)
        ( f(g(v1), g(v2)), st2)
    }

    def unOp[T](currN: Int, breakN: Int, e: Expr, env: LettuceEnvironment, st: LettuceStore) (g: Value => T) (f: T => Value) : (Value, LettuceStore) = {
        val (v, st1) = evalExpr(currN, breakN, e, env, st)
        (f(g(v)), st1)
    }

    def evalExpr(currN: Int, breakN: Int, e: Expr, env: LettuceEnvironment, st: LettuceStore): (Value, LettuceStore) = {
        if (currN == breakN) {

            (BreakValue(e, env, st), st)
        } else {
            val newN = currN + 1

            e match {
                case ConstNum(f) => (NumValue(f), st)
                case ConstBool(b) => (BoolValue(b), st)
                case Ident(s) => (env.lookup(s), st)

                case Plus(e1, e2) => binOp(currN, breakN, e1, e2, env, st) {
                    LettuceValue.valueToNum
                } { case (v1, v2) => NumValue(v1 + v2) }


                case Minus(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } { case (v1, v2) => NumValue(v1 - v2) }


                case Mult(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } { case (v1, v2) => NumValue(v1 * v2) }


                case Div(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } { case (v1, v2) =>
                        if (v2 != 0.0) {
                            NumValue(v1 / v2)
                        }
                        else {
                            throw new RuntimeError("Division by zero")
                        }
                    }


                case Log(e1) =>
                    unOp(currN, breakN,e1, env, st) {
                        LettuceValue.valueToNum
                    } {
                        v =>
                            if (v <= 0) {
                                throw new RuntimeError("Logarithm of a non positive number")
                            } else {
                                NumValue(math.log(v))
                            }
                    }


                case Exp(e1) =>
                    unOp(currN, breakN,e1, env, st) {
                        LettuceValue.valueToNum
                    } { v => NumValue(math.exp(v)) }


                case Sine(e1) =>
                    unOp(currN, breakN,e1, env, st) {
                        LettuceValue.valueToNum
                    } { v => NumValue(math.sin(v)) }


                case Cosine(e1) =>
                    unOp(currN, breakN,e1, env, st) {
                        LettuceValue.valueToNum
                    } { v => NumValue(math.cos(v)) }


                case Geq(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } {
                        case (v1, v2) => BoolValue(v1 >= v2)
                    }


                case Eq(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } {
                        case (v1, v2) => BoolValue(v1 == v2)
                    }


                case Gt(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } {
                        case (v1, v2) => BoolValue(v1 > v2)
                    }


                case Neq(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToNum
                    } {
                        case (v1, v2) => BoolValue(v1 != v2)
                    }


                case And(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToBool
                    } {
                        case (v1, v2) => BoolValue(v1 && v2)
                    }


                case Or(e1, e2) =>
                    binOp(currN, breakN, e1, e2, env, st) {
                        LettuceValue.valueToBool
                    } {
                        case (v1, v2) => BoolValue(v1 || v2)
                    }


                case Not(e1) =>
                    unOp(currN, breakN,e1, env, st) {
                        LettuceValue.valueToBool
                    } {
                        v => BoolValue(!v)
                    }


                case IfThenElse(eC, e1, e2) =>
                    val (v, st1) = evalExpr(newN, breakN, eC, env, st)
                    val b = LettuceValue.valueToBool(v)
                    if (b)
                        evalExpr(newN, breakN, e1, env, st1)
                    else
                        evalExpr(newN, breakN, e2, env, st1)


                case Block(eList) =>
                    if (eList.isEmpty) {
                        throw new SyntaxError("block of zero length is not allowed")
                    } else {
                        eList.foldLeft[(Value, LettuceStore)](NumValue(-1), st) { case ((_, st1), eHat) => evalExpr(newN, breakN, eHat, env, st1) }
                    }


                case Let(x, e1, e2) =>
                    val (v1, st1) = evalExpr(newN, breakN, e1, env, st)
                    val newEnv = EnvironmentUtils.make_extension(List(x), List(v1), env)
                    evalExpr(newN, breakN, e2, newEnv, st1)


                case LetRec(f, fd, e2) => fd match {
                    case FunDef(xList, e1) =>
                        val newEnv = ExtendEnvRec(f, xList, e1, env)
                        evalExpr(newN, breakN, e2, newEnv, st)

                }

                case FunDef(xList, e1) => (Closure(xList, e1, env), st)

                case FunCall(fExpr, aList) =>
                    val (v, st1) = evalExpr(newN, breakN, fExpr, env, st)
                    val vc = LettuceValue.valueToClosure(v)
                    val (vList, stAfterArgEval) = aList.foldLeft[(List[Value], LettuceStore)](List(), st1) {
                        case ((vList1, st2), newExpr) =>
                            val (v, st3) = evalExpr(newN, breakN, newExpr, env, st2)
                            (vList1 ++ List(v), st3)

                    }
                    vc match {
                        case Closure(fArgs, eHat, capturedEnv) =>
                            val newEnv = EnvironmentUtils.make_extension(fArgs, vList, capturedEnv)
                            evalExpr(newN, breakN, eHat, newEnv, stAfterArgEval)

                        //case _ => throw new TypeConversionError("Converting from non closure to a closure value")
                    }

                case NewRef(e1) =>
                    val (v, st1) = evalExpr(newN, breakN, e1, env, st)
                    val (j, st2) = StoreInterface.mkNewReference(st1, v)
                    (Reference(j), st2)


                case DeRef(e1) =>
                    val (v, st1) = evalExpr(newN, breakN, e1, env, st)
                    val j = LettuceValue.valueToReference(v)
                    (StoreInterface.mkDeref(j, st1), st1)


                case AssignRef(e1, e2) =>
                    val (v1, st1) = evalExpr(newN, breakN, e1, env, st)
                    val j = LettuceValue.valueToReference(v1)
                    val (v2, st2) = evalExpr(newN, breakN, e2, env, st1)
                    val st3 = StoreInterface.mkAssign(j, v2, st2)
                    (v2, st3)

            }
        }
    }
}

