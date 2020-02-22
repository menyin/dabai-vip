package com.gupao.edu.vip

import org.gradle.api.Plugin
import org.gradle.api.Project


class MyCustomPlugin implements Plugin<Project>{
    @Override
    void apply(Project target) {
        target.task('myTask'){
           println 'my plugin is myTask'
       }
    }
}
