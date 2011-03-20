require 'fileutils'
i = 0
Dir.new("adapted_docs").select{|name| name != "." and name != ".."}.each do |name|
	if File.size("adapted_docs/#{name}") <= 10
		i = i + 1
		puts name
		FileUtils.rm "adapted_docs/#{name }"
	end
end
puts "#{i} files removed"

missing = {}
Dir.new("adapted_topics").select{|name| name != "." and name != ".."}.each do |name|
	open "adapted_topics/#{name}", "r" do |file|
		open "tmp", "w" do |tmp|
			while (line = file.gets)
				if File.exist? "adapted_docs/#{line.strip}"
					tmp.puts line.strip
				else
					missing[line.strip] = true
					puts line.strip
				end
			end
		end
	end
	FileUtils.cp "tmp", "ad/#{name}"
end
puts "#{missing.length} files dereferenced"


