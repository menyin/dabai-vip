package com.gupaoedu.vip.groovy.variable

//闭包
//    def clouser  = {
//        return  "Hello"
//
//    }
//
//    println clouser.call()

//def clouser2  = { return  "Hello ${it}"}
//print(clouser2.call())


//def clouser3 = {println item ++}
//clouser3.call()

//c = {it}
//println c('run')

//def clouser3  = { name -> return  "Hello ${name}"}
//def result = clouser3('groovy!')
//println result
//

int x = 10
//用来求指定number的阶乘
int fab(int number){
    int result = 1
    //result = result * num

    1.upto(number,{num -> result *= num})
    return result
}

//println fab(5)
//
//int fab2(int number){
//    int result = 1
//    1.downto(1){
//        num -> result *= num
//    }
//
//    return result
//}
//

3.times{
//    println it
}


//for (int i = 0; i <3 ; i++) {
//    println i
//}
int cal(int number){
    int result = 0
    number.times{
        num -> result  += num
    }
    return result

}
//println cal(5)

/**
 *
 * 字符串与闭包
 */

 String str = 'abcd'
 str.each {
//     String temp -> println(temp)
 }


println str.find{
//    String s -> s.isNumber()
}
//
def scriptClouser = {
//

}
//scriptClouser.call()
//
//class Person {
//    def static classClouser = {
//        println "scriptClouser this:"+this    //代表闭包定义处的类
//        println "scriptClouser this:"+owner  //代表闭包定义处的类或对象
//        println "scriptClouser this:"+delegate //代表任意对象，默认与owner一样
//    }
//
//    def static say() {
//        def classClouser = {
//            println "scriptClouser this:"+this    //代表闭包定义处的类
//            println "scriptClouser this:"+owner  //代表闭包定义处的类或对象
//            println "scriptClouser this:"+delegate //代表任意对象，默认与owner一样
//        }
//        classClouser.call()
//    }
//
//
//}
//Person.classClouser.call()
//Person.say()
//scriptClouser.call()
//
////闭包中定义闭包
//def nestClouser = {
//    def innerClouser = {
//        println "innserClouser this:"+this    //代表闭包定义处的类
//        println "innserClouser this:"+owner  //代表闭包定义处的类或对象
//        println "innserClouser this:"+delegate //代表任意对象，默认与owner一样
//    }
//    innerClouser.call()
//
//    println "nestClouser this:"+this    //代表闭包定义处的类
//    println "nestClouser this:"+owner  //代表闭包定义处的类或对象
//    println "nestClouser this:"+delegate //代表任意对象，默认与owner一样
//}
//nestClouser.call()

//字符串调与闭包的结合使用
String str2 = 'the 2 and 5 is 7'
//each
//str2.each {
//    String temp -> println temp
//}

//find来查找符合条件的第一个
println str2.find {
    String s -> s.isNumber()
}

def list = str2.findAll { String s -> s.isNumber()}
//    println list.toListString()

//def result = str2.any {
//    String s -> s.isNumber()
//}
//
//println result
def result = str2.every {
    String s -> s.isNumber()
}

//println result


def list2 = str2.collect{
    it.toUpperCase()
}

println list2.toListString()





























