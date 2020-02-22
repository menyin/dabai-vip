package com.gupaoedu.vip.groovy.variable


//int x = 10
//
//println x.class
//
//double y = 3.14
//println y.class

def x_1 = 11

println x_1.class

x_1 = '11'

println x_1.class

def color = [red:"ff0000",green:"00ff00",blue:"0000ff"]
color.yellow = 'ffff00'
println color['red']
color.(pink) = 'ff00ff'
//println color.getClass()
println color.toMapString()