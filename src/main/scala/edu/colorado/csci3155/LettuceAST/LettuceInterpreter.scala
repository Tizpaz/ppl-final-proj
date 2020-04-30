package LettuceAST


case object LettuceInterpreter {
    def evalProgram(p: Program, breakN: Int, pB: Program): Value = {
//        val k = (v: Value, s: StoreInterface) => {(v, s)}
        p match {
            //        case TopLevel(BreakValue(c, eB, envB, stB)) => {
            //            throw new IllegalArgumentException(s"BreakValue: at a 'Top Level' Program: p: $p\n")
            //        }
            case TopLevel(e) => {
                pB match {
                    case EmptyTopLevel => {
                        val (v, st) = evalExprCPS(EmptyExpression, 0, breakN, e,
                            EmptyEnvironment.asInstanceOf[LettuceEnvironment],
                            StoreInterface.emptyStore, (v: Value, s: LettuceStore) => {(v, s)})
                        v
                    }

                    case TopLevel(let@Let(_, _, _)) => {

                        val (v, st) = evalExprCPS(let, 0, -1, e, EmptyEnvironment.asInstanceOf[LettuceEnvironment], StoreInterface.emptyStore, (v: Value, s: LettuceStore) => {(v, s)})
                        v

                    }

                    case TopLevel(funCall@FunCall(_, _)) => {
                        val (v, st) = evalExprCPS(funCall, 0, -1, e, EmptyEnvironment.asInstanceOf[LettuceEnvironment], StoreInterface.emptyStore, (v: Value, s: LettuceStore) => {(v, s)})
                        v
                    }

                    case _ => throw new IllegalArgumentException(s"Error: not a ' Break Top Level' Program: p: $p\n")

                }

            }
            case _ => throw new IllegalArgumentException(s"Error: not a 'Top Level' Program: p: $p\n")
        }
    }

//    def binOpIDK[T](eB: Expr, currN: Int, breakN: Int, e1: Expr, e2: Expr, env: LettuceEnvironment, st: LettuceStore )
//                (g: Value => T)
//                (f: (T,T) => Value): (Value, LettuceStore) = {
//        val (v1, st1) = evalExprCPS(eB,currN, breakN, e1, env, st, k)
//        val (v2, st2) = evalExprCPS(eB,currN, breakN, e2, env, st1, k)
//        ( f(g(v1), g(v2)), st2)
//    }
//
//    def unOpIDK[T](eB: Expr,currN: Int, breakN: Int, e: Expr, env: LettuceEnvironment, st: LettuceStore) (g: Value => T) (f: T => Value) : (Value, LettuceStore) = {
//        val (v, st1) = evalExprCPS(eB,currN, breakN, e, env, st)
//        (f(g(v)), st1)
//    }



//    def evalExprCPS[T](eB: Expr, currN: Int, breakN: Int, e: Expr, env: LettuceEnvironment, st: LettuceStore): (Value, LettuceStore) = {
    def evalExprCPS[V](eB: Expr, currN: Int, breakN: Int, e: Expr, env: LettuceEnvironment, st: LettuceStore, k: (Value, LettuceStore) => (Value,LettuceStore)): (Value,LettuceStore) = {

        def binOp(e1: Expr, e2: Expr) (fun: (Double , Double) =>  Double )  = {
            evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToNumCPS(u1, st1, (v1, st1) =>
            evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToNumCPS(u2, st2, (v2, st2) =>
                k( NumValue(fun(v1, v2)), st2)))))

        }


        def unOp(e1: Expr)(fun: Double => Double) = {
            evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToNumCPS(u1, st1, (v1, st1) =>
                 k( NumValue( fun(v1)), st1)))
        }

        def compNumOp (e1: Expr, e2: Expr) (fun: (Double , Double) => Boolean)  = {
            evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToNumCPS(u1, st1, (v1, st1) =>
                evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToNumCPS(u2, st2, (v2, st2) =>
                    k( BoolValue(fun(v1, v2)), st2)))))
        }


        def compBoolOp (e1: Expr, e2: Expr) (fun: (Boolean , Boolean) => Boolean)  = {
            evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToBoolCPS(u1, st1, (v1, st1) =>
                evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToBoolCPS(u2, st2, (v2, st2) =>
                    k( BoolValue(fun(v1, v2)), st2)))))
        }

        def compUnOp(e1: Expr)(fun: Boolean => Boolean) = {
            evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToBoolCPS(u1, st1, (v1, st1) =>
                k( BoolValue( fun(v1)), st1)))
        }


        println(s"currN: $currN, eB: $eB\n")


            e match {
                case ConstNum(f) => k(NumValue(f), st)
                case ConstBool(b) => k(BoolValue(b), st)
                case Ident(s) => k(env.lookup(s), st)

                case Plus(e1, e2) => binOp(e1, e2) ( _ + _ )
                case Minus(e1, e2) => binOp(e1, e2) ( _ - _ )
                case Mult(e1, e2) =>  binOp(e1, e2) (_ * _)
                case Div(e1, e2) => binOp(e1, e2) {
                    case (_, 0.0) => throw new RuntimeError("Division by zero")
                    case (v1, v2) => (v1/v2)
                }

                case Log(e1) => unOp(e1) {
                    case v if v > 0.0 => math.log(v)
                    case v => throw new RuntimeError(s"Log of a negative number ${e} evaluates to ${v}!")
                }
                case Exp(e1) => unOp(e1)(math.exp)
                case Sine(e1) => unOp(e1)(math.sin)
                case Cosine(e1) => unOp(e1)(math.cos)

                case Geq(e1, e2) => compNumOp (e1, e2) ( _ >= _ )
                case Eq(e1, e2) => compNumOp (e1, e2) ( _ == _ )
                case Gt(e1, e2) => compNumOp (e1, e2) ( _ > _ )
                case Neq(e1, e2) => compNumOp (e1, e2) ( _ != _ )
//
                case And(e1, e2) => {
                    // TODO: Check if it "Short circuit" eval of And
                    evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToBoolCPS(u1, st1, (v1, st1) => {
                    evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToBoolCPS(u2, st2, (v2, st2) =>
                                k(BoolValue( v1 && v2 ), st2 )
                        ))}
                    ))
                }

                case Or(e1, e2) => {
                    // TODO: Check if it "Short circuit" eval of Or
                    evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToBoolCPS(u1, st1, (v1, st1) => {
                        evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToBoolCPS(u2, st2, (v2, st2) =>
                            k(BoolValue( v1 || v2 ), st2 )
                        ))}
                    ))
                }

                case Not(e1) => {
                    evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToBoolCPS(u1, st1, (v1, st1) => {
                        k(BoolValue( !v1), st1)
                    }))

                }

                case IfThenElse(eC, e1, e2) => {
                    evalExprCPS(eB, currN, breakN, eC, env, st, {

                        case (BoolValue(true), st1) => evalExprCPS(eB, currN, breakN, e1, env, st, k)
                        case (BoolValue(false), st1) => evalExprCPS(eB, currN, breakN, e2, env, st, k)
                        case _ => throw new RuntimeError(s"If-then-else condition expr: ${e1} is non-boolean")

                    })}






//

//                case Block(eList) => // TODO: Is this needed?
//                    if (eList.isEmpty) {
//                        throw new SyntaxError("block of zero length is not allowed")
//                    } else {
//                        eList.foldLeft[(Value, LettuceStore)](NumValue(-1), st) { case ((_, st1), eHat) => evalExprCPS(eB, currN, breakN, eHat, env, st1) }
//                    }


                case Let(x, e1, e2) => {
                    eB match {
                        case Let(eBs, eBe, _) if (x == eBs && e1 == eBe) => {
//                            (BreakValue(currN, e, env, st), st) // Notes
                            k(BreakValue( e, env, st, eBs, eBe, currN, breakN), st)
                        }
                        case Let(eBs, eBe, _)  => {
                            if(currN == breakN) { // DEBUG maybe this isnt good
                                k(BreakValue( e, env, st, eBs, eBe, currN, breakN), st)
                            } else {
                                val (v1, st1) = evalExprCPS(eB, currN, breakN, e1, env, st, k)
                                val newEnv = EnvironmentUtils.make_extension(List(x), List(v1), env)
                                evalExprCPS(eB, currN + 1, breakN, e2, newEnv, st1, k)
                            }


                        }

                        case _ => {

                            val (v1, st1) = evalExprCPS(eB, currN, breakN, e1, env, st, k)
                            val newEnv = EnvironmentUtils.make_extension(List(x), List(v1), env)
                            evalExprCPS(eB, currN + 1, breakN, e2, newEnv, st1, k)


//                            evalExprCPS(eB,currN, breakN, env, st, (u1, st1) =>  (v1, st1) => { // Where should curr + 1 go ?
//                                evalExprCPS(eB, currN + 1, breakN, e2,
//                                    newEnv =>  EnvironmentUtils.make_extension(List(x),List(v1), env), st1, k)}


                        }

                    }
                }
//
//evalExprCPS(eB, currN, breakN, e1, env, st, (u1, st1) => LettuceValue.valueToNumCPS(u1, st1, (v1, st1) =>
                //            evalExprCPS(eB, currN, breakN, e2, env, st1, (u2, st2) => LettuceValue.valueToNumCPS(u2, st2, (v2, st2) =>
                //                k( NumValue(fun(v1, v2)), st2)))))
//                case Let(x, e1, e2) =>
//                    //                    println(s"eB: $eB\n e: $e x = 1 in let y = 2 in let z = 3 in x + y + z;;")
//
//                    if (currN == breakN) {
//                        (BreakValue(currN, e, env, st), st)
//
//                    } else {
//                        eB match {
//                            case Let(xeB, e1eB, _) if (x == xeB && e1 == e1eB) => {
//                                (BreakValue(currN, e, env, st), st)
//                            }
//                            case _ => {
//
//                                val (v1, st1) = evalExprCPS(eB, currN, breakN, e1, env, st)
//                                val newEnv = EnvironmentUtils.make_extension(List(x), List(v1), env)
//                                evalExprCPS(eB, currN + 1, breakN, e2, newEnv, st1)
//                            }
//                        }
//                    }
//
//
//                case LetRec(f, fd, e2) => {
//
//                    println(s"f: $f\nfd: $fd\ne2: $e2, currN: $currN, breakN: $breakN\n")
//                    fd match {
//
//
//                        case FunDef(xList, e1) =>
//                            println(s"FunDef(xList = $xList, e1 = $e1\ne2 =$e2)\n")
//
//
//                                val newEnv = ExtendEnvRec(f, xList, e1, env)
//                                evalExprCPS(eB, currN, breakN, e2, newEnv, st)
//
//
//
//
//
//                    }
//                }
//
//                case FunDef(xList, e1) => (Closure(xList, e1, env), st)
//
//                case FunCall(fExpr, aList) =>
//                    println(s"FunCall(fExpr:  $fExpr\naList: $aList\n currN: $currN, breakN: $breakN\n")
//
//                    if (currN == breakN) {
//                        println("here?")
//
//                        (BreakValue(currN, e, env, st), st)
//
//                    } else {
//                        eB match {
//                            case FunCall(eBCall, listB) if ( fExpr ==  eBCall && aList == listB) =>
//                                (BreakValueRec(currN, e, env, st), st)
//
//                            case _ =>  {
//                                val (v, st1) = evalExprCPS(eB, currN, breakN, fExpr, env, st)
//                                val vc = LettuceValue.valueToClosure(v)
//                                val (vList, stAfterArgEval) = aList.foldLeft[(List[Value], LettuceStore)](List(), st1) {
//                                    case ((vList1, st2), newExpr) =>
//                                        val (v, st3) = evalExprCPS(eB, currN + 1, breakN, newExpr, env, st2)
//                                        (vList1 ++ List(v), st3)
//
//                                }
//                                vc match {
//                                    case Closure(fArgs, eHat, capturedEnv) =>
//                                        val newEnv = EnvironmentUtils.make_extension(fArgs, vList, capturedEnv)
//                                        evalExprCPS(eB, currN+1, breakN, eHat, newEnv, stAfterArgEval)
//
//                                    //case _ => throw new TypeConversionError("Converting from non closure to a closure value")
//                                }
//                            }
//                        }
//
//                    }
//
//
//                case NewRef(e1) =>
//                    val (v, st1) = evalExprCPS(eB,currN, breakN, e1, env, st)
//                    val (j, st2) = StoreInterface.mkNewReference(st1, v)
//                    (Reference(j), st2)
//
//
//                case DeRef(e1) =>
//                    val (v, st1) = evalExprCPS(eB,currN, breakN, e1, env, st)
//                    val j = LettuceValue.valueToReference(v)
//                    (StoreInterface.mkDeref(j, st1), st1)
//
//
//                case AssignRef(e1, e2) =>
//                    val (v1, st1) = evalExprCPS(eB,currN, breakN, e1, env, st)
//                    val j = LettuceValue.valueToReference(v1)
//                    val (v2, st2) = evalExprCPS(eB,currN, breakN, e2, env, st1)
//                    val st3 = StoreInterface.mkAssign(j, v2, st2)
//                    (v2, st3)
//
//                }
            }

     }
}
