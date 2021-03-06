package elaborator;

import java.util.LinkedList;

import ast.Ast.Class;
import ast.Ast.Class.ClassSingle;
import ast.Ast.Dec;
import ast.Ast.Exp;
import ast.Ast.Exp.Add;
import ast.Ast.Exp.And;
import ast.Ast.Exp.ArraySelect;
import ast.Ast.Exp.Call;
import ast.Ast.Exp.False;
import ast.Ast.Exp.Id;
import ast.Ast.Exp.Length;
import ast.Ast.Exp.Lt;
import ast.Ast.Exp.NewIntArray;
import ast.Ast.Exp.NewObject;
import ast.Ast.Exp.Not;
import ast.Ast.Exp.Num;
import ast.Ast.Exp.Sub;
import ast.Ast.Exp.This;
import ast.Ast.Exp.Times;
import ast.Ast.Exp.True;
import ast.Ast.MainClass;
import ast.Ast.Method;
import ast.Ast.Method.MethodSingle;
import ast.Ast.Program.ProgramSingle;
import ast.Ast.Stm;
import ast.Ast.Stm.Assign;
import ast.Ast.Stm.AssignArray;
import ast.Ast.Stm.Block;
import ast.Ast.Stm.If;
import ast.Ast.Stm.Print;
import ast.Ast.Stm.While;
import ast.Ast.Type;
import ast.Ast.Type.ClassType;
import control.Control.ConAst;

public class ElaboratorVisitor implements ast.Visitor {
    public ClassTable classTable; // symbol table for class
    public MethodTable methodTable; // symbol table for each method
    public String currentClass; // the class name being elaborated
    public Type.T type; // type of the expression being elaborated

    public ElaboratorVisitor() {
        this.classTable = new ClassTable();
        this.methodTable = new MethodTable();
        this.currentClass = null;
        this.type = null;
    }

    private void error(int lineNum) {
        System.out.println("in line: " + lineNum);
        System.out.println("type mismatch");
        System.exit(1);
    }

    public LinkedList<String> findBase(String classType) {
        LinkedList<String> classBases = new LinkedList<>();
        classBases.add(classType);
        ClassBinding cb = this.classTable.get(classType);
        while (cb.extendss != null) {
            classBases.add(cb.extendss);
            cb = this.classTable.get(cb.extendss);
        }
        return classBases;
    }

    // /////////////////////////////////////////////////////
    // expressions
    @Override
    public void visit(Add e) {
        e.left.accept(this);
        if (!this.type.toString().equals("@int"))
            error(e.left.lineNum);
        e.right.accept(this);
        if (!this.type.toString().equals("@int"))
            error(e.right.lineNum);
        this.type = new Type.Int(e.right.lineNum);
        return;
    }

    @Override
    public void visit(And e) {
        e.left.accept(this);
        if (!this.type.toString().equals("@boolean"))
            error(e.left.lineNum);
        e.right.accept(this);
        if (!this.type.toString().equals("@boolean"))
            error(e.right.lineNum);
        this.type = new Type.Boolean(e.right.lineNum);
        return;
    }

    @Override
    public void visit(ArraySelect e) {
        e.index.accept(this);
        if (!this.type.toString().equals("@int"))
            error(e.lineNum);
        e.array.accept(this);
        if (!this.type.toString().equals("@int[]"))
            error(e.array.lineNum);
        this.type = new Type.Int(e.array.lineNum);
        return;
    }

    @Override
    public void visit(Call e) {
        Type.T leftty;
        Type.ClassType ty = null;

        e.exp.accept(this);
        leftty = this.type;
        if (leftty instanceof ClassType) {
            ty = (ClassType) leftty;
            e.type = ty.id;
        } else
            error(e.lineNum);
        MethodType mty = this.classTable.getm(ty.id, e.id);
        java.util.LinkedList<Type.T> argsty = new LinkedList<Type.T>();
        if (e.args != null) {
            for (Exp.T a : e.args) {
                a.accept(this);
                argsty.addLast(this.type);
            }
        }
        if (mty.argsType.size() != argsty.size())
            error(e.lineNum);
        for (int i = 0; i < argsty.size(); i++) {
            Dec.DecSingle dec = (Dec.DecSingle) mty.argsType.get(i);
            if (argsty.get(i) instanceof Type.ClassType) {
                boolean isMatch = false;
                LinkedList<String> classTypes = findBase(argsty.get(i).toString());
                for (String type : classTypes) {
                    if (dec.type.toString().equals(type))
                        isMatch = true;
                }
                if (!isMatch)
                    error(e.lineNum);
            } else {
                if (dec.type.toString().equals(argsty.get(i).toString()))
                    ;
                else
                    error(e.lineNum);
            }
        }
        this.type = mty.retType;
        e.at = argsty;
        e.rt = this.type;
        return;
    }

    @Override
    public void visit(False e) {
        this.type = new Type.Boolean(e.lineNum);
    }

    @Override
    public void visit(Id e) {
        // first look up the id in method table
        Type.T type = this.methodTable.get(e.id);
        // if search failed, then s.id must be a class field.
        if (type == null) {
            type = this.classTable.get(this.currentClass, e.id);
            // mark this id as a field id, this fact will be
            // useful in later phase.
            e.isField = true;
        }
        if (type == null)
            error(e.lineNum);
        this.type = type;
        // record this type on this node for future use.
        e.type = type;
        return;
    }

    @Override
    public void visit(Length e) {
        e.array.accept(this);
        this.type = new Type.Int(e.lineNum);
    }

    @Override
    public void visit(Lt e) {
        e.left.accept(this);
        Type.T ty = this.type;
        e.right.accept(this);
        if (!this.type.toString().equals(ty.toString()))
            error(e.right.lineNum);
        this.type = new Type.Boolean(e.right.lineNum);
        return;
    }

    @Override
    public void visit(NewIntArray e) {
        e.exp.accept(this);
        if (!this.type.toString().equals("@int"))
            error(e.exp.lineNum);
        this.type = new Type.IntArray(e.lineNum);
        return;
    }

    @Override
    public void visit(NewObject e) {
        this.type = new Type.ClassType(e.id, e.lineNum);
        return;
    }

    @Override
    public void visit(Not e) {
        e.exp.accept(this);
        if (!this.type.toString().equals("@boolean"))
            error(e.lineNum);
        return;
    }

    @Override
    public void visit(Num e) {
        this.type = new Type.Int(e.lineNum);
        return;
    }

    @Override
    public void visit(Sub e) {
        e.left.accept(this);
        Type.T leftty = this.type;
        e.right.accept(this);
        if (!this.type.toString().equals(leftty.toString()))
            error(e.right.lineNum);
        this.type = new Type.Int(e.right.lineNum);
        return;
    }

    @Override
    public void visit(This e) {
        this.type = new Type.ClassType(this.currentClass, e.lineNum);
        return;
    }

    @Override
    public void visit(Times e) {
        e.left.accept(this);
        Type.T leftty = this.type;
        e.right.accept(this);
        if (!this.type.toString().equals(leftty.toString()))
            error(e.right.lineNum);
        this.type = new Type.Int(e.right.lineNum);
        return;
    }

    @Override
    public void visit(True e) {
        this.type = new Type.Boolean(e.lineNum);
        return;
    }

    // statements
    @Override
    public void visit(Assign s) {
        // first look up the id in method table
        Type.T type = this.methodTable.get(s.id.id);
        // if search failed, then s.id must
        if (type == null)
            type = this.classTable.get(this.currentClass, s.id.id);
        if (type == null)
            error(s.lineNum);
        s.id.accept(this);
        s.exp.accept(this);
        s.type = type;
        if (this.type instanceof ClassType) {
            boolean isMatch = false;
            LinkedList<String> classTypes = findBase(this.type.toString());
            for (String t : classTypes) {
                if (type.toString().equals(t))
                    isMatch = true;
            }
            if (!isMatch)
                error(s.lineNum);

        } else {
            if (!this.type.toString().equals(type.toString()))
                error(s.lineNum);
        }
        return;
    }

    @Override
    public void visit(AssignArray s) {
        Type.T idType = this.methodTable.get(s.id.id);
        if (idType == null)
            idType = this.classTable.get(this.currentClass, s.id.id);
        if (idType == null)
            error(s.lineNum);
        s.id.accept(this);
        if (!idType.toString().equals("@int[]"))
            error(s.lineNum);
        idType = new Type.Int(s.lineNum);
        s.index.accept(this);
        if (!this.type.toString().equals("@int"))
            error(s.lineNum);
        s.exp.accept(this);
        if (!this.type.toString().equals(idType.toString()))
            error(s.lineNum);
        return;
    }

    @Override
    public void visit(Block s) {
        for (Stm.T stm : s.stms) {
            stm.accept(this);
        }
    }

    @Override
    public void visit(If s) {
        s.condition.accept(this);
        if (!this.type.toString().equals("@boolean"))
            error(s.lineNum);
        s.thenn.accept(this);
        s.elsee.accept(this);
        return;
    }

    @Override
    public void visit(Print s) {
        s.exp.accept(this);
        if (!this.type.toString().equals("@int"))
            error(s.lineNum);
        return;
    }

    @Override
    public void visit(While s) {
        s.condition.accept(this);
        if (!type.toString().equals("@boolean"))
            error(s.lineNum);
        s.body.accept(this);
    }

    // type
    @Override
    public void visit(Type.Boolean t) {
        this.type = new Type.Boolean(t.lineNum);
    }

    @Override
    public void visit(Type.ClassType t) {
        this.type = new ClassType(t.id, t.lineNum);
    }

    @Override
    public void visit(Type.Int t) {
        this.type = new Type.Int(t.lineNum);
    }

    @Override
    public void visit(Type.IntArray t) {
        this.type = new Type.IntArray(t.lineNum);
    }

    // dec
    @Override
    public void visit(Dec.DecSingle d) {
    }

    // method
    @Override
    public void visit(Method.MethodSingle m) {
        // construct the method table
        for (Dec.T dec : m.locals) {
            dec.accept(this);
        }

        this.methodTable.put(m.formals, m.locals);

        if (ConAst.elabMethodTable)
            this.methodTable.dump();

        for (Stm.T s : m.stms)
            s.accept(this);
        m.retExp.accept(this);
        methodTable.clear();
        return;
    }

    // class
    @Override
    public void visit(Class.ClassSingle c) {
        this.currentClass = c.id;

        for (Method.T m : c.methods) {
            m.accept(this);
        }
        return;
    }

    // main class
    @Override
    public void visit(MainClass.MainClassSingle c) {
        this.currentClass = c.id;
        // "main" has an argument "arg" of type "String[]", but
        // one has no chance to use it. So it's safe to skip it...

        c.stm.accept(this);
        return;
    }

    // ////////////////////////////////////////////////////////
    // step 1: build class table
    // class table for Main class
    private void buildMainClass(MainClass.MainClassSingle main) {
        this.classTable.put(main.id, new ClassBinding(null));
    }

    // class table for normal classes
    private void buildClass(ClassSingle c) {
        this.classTable.put(c.id, new ClassBinding(c.extendss));
        for (Dec.T dec : c.decs) {
            Dec.DecSingle d = (Dec.DecSingle) dec;
            this.classTable.put(c.id, d.id, d.type);
        }
        for (Method.T method : c.methods) {
            MethodSingle m = (MethodSingle) method;
            this.classTable.put(c.id, m.id, new MethodType(m.retType, m.formals));
        }
    }

    // step 1: end
    // ///////////////////////////////////////////////////

    // program
    @Override
    public void visit(ProgramSingle p) {
        // ////////////////////////////////////////////////
        // step 1: build a symbol table for class (the class table)
        // a class table is a mapping from class names to class bindings
        // classTable: className -> ClassBinding{extends, fields, methods}
        buildMainClass((MainClass.MainClassSingle) p.mainClass);
        for (Class.T c : p.classes) {
            buildClass((ClassSingle) c);
        }

        // we can double check that the class table is OK!
        if (control.Control.ConAst.elabClassTable) {
            this.classTable.dump();
        }

        // ////////////////////////////////////////////////
        // step 2: elaborate each class in turn, under the class table
        // built above.
        p.mainClass.accept(this);
        for (Class.T c : p.classes) {
            c.accept(this);
        }

    }
}
