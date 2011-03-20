require 'fileutils'

class_size = 200
class_names = ["crude", "grain", "money-fx", "ship", "trade"]
classes = {}
class_names.each do |class_name|
	open "adapted_topics/#{class_name}", "r" do |class_file|
		classes[class_name] = []
		200.times {classes[class_name] << class_file.gets.strip} 
	end
end

200.times do |i|
	classes.each do |class_name, docs|
		FileUtils.copy_file "adapted_docs/#{docs[i]}", "test_docs/#{docs[i]}"
	end
end

classes.each do |class_name, docs|
	open "test_topics/#{class_name}", "w" do |class_file|
		docs.each{|doc| class_file.puts doc}
	end
end